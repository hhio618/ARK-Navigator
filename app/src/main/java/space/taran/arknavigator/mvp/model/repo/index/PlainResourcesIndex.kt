package space.taran.arknavigator.mvp.model.repo.index

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import space.taran.arknavigator.mvp.model.dao.Resource
import space.taran.arknavigator.mvp.model.dao.ResourceDao
import space.taran.arknavigator.mvp.model.dao.ResourceExtra
import space.taran.arknavigator.mvp.model.dao.ResourceWithExtra
import space.taran.arknavigator.mvp.model.repo.kind.GeneralKindFactory
import space.taran.arknavigator.mvp.model.repo.preview.PreviewStorage
import space.taran.arknavigator.utils.LogTags.PREVIEWS
import space.taran.arknavigator.utils.LogTags.RESOURCES_INDEX
import space.taran.arknavigator.utils.listChildren
import space.taran.arknavigator.utils.withContextAndLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.system.measureTimeMillis

internal data class Difference(
    val deleted: List<Path>,
    val updated: List<Path>,
    val added: List<Path>
)

@OptIn(ExperimentalPathApi::class)
// The index must read from the DAO only during application startup,
// since DB doesn't change from outside. But we must persist all changes
// during application lifecycle into the DAO for the case of any unexpected exit.
class PlainResourcesIndex internal constructor(
    private val root: Path,
    private val dao: ResourceDao,
    private val previewStorage: PreviewStorage,
    resources: Map<Path, ResourceMeta>
) : ResourcesIndex {
    private val _kindDetectFailedFlow: MutableSharedFlow<Path> = MutableSharedFlow()

    override val kindDetectFailedFlow: SharedFlow<Path> = _kindDetectFailedFlow

    private val mutex = Mutex()

    internal val metaByPath: MutableMap<Path, ResourceMeta> =
        resources.toMutableMap()

    private val pathById: MutableMap<ResourceId, Path> =
        resources.map { (path, meta) ->
            meta.id to path
        }
            .toMap()
            .toMutableMap()

    override suspend fun listResources(
        prefix: Path?
    ): Set<ResourceMeta> = mutex.withLock {
        val metas = if (prefix != null) {
            metaByPath.filterKeys { it.startsWith(prefix) }
        } else {
            metaByPath
        }.values

        Log.d(RESOURCES_INDEX, "${metas.size} resources returned")
        return metas.toSet()
    }

    fun contains(id: ResourceId) = pathById.containsKey(id)

    override suspend fun getPath(id: ResourceId): Path = mutex.withLock {
        tryGetPath(id)!!
    }

    override suspend fun getMeta(id: ResourceId): ResourceMeta = mutex.withLock {
        tryGetMeta(id)!!
    }

    override suspend fun remove(id: ResourceId): Path = mutex.withLock {
        Log.d(RESOURCES_INDEX, "forgetting resource $id")
        return tryRemove(id)!!
    }

    override suspend fun reindex(): Unit =
        withContextAndLock(Dispatchers.IO, mutex) {
            reindexRoot(calculateDifference())
        }

    // should be only used in AggregatedResourcesIndex
    fun tryGetPath(id: ResourceId): Path? = pathById[id]

    // should be only used in AggregatedResourcesIndex
    fun tryGetMeta(id: ResourceId): ResourceMeta? {
        val path = tryGetPath(id)
        if (path != null) {
            return metaByPath[path]
        }
        return null
    }

    // should be only used in AggregatedResourcesIndex
    fun tryRemove(id: ResourceId): Path? {
        val path = pathById.remove(id) ?: return null

        val idRemoved = metaByPath.remove(path)!!.id

        if (id != idRemoved) {
            throw AssertionError("internal mappings are diverged")
        }

        val duplicatedResource = metaByPath
            .entries
            .find { entry -> entry.value.id == idRemoved }
        duplicatedResource?.let { entry ->
            pathById[entry.value.id] = entry.key
        }

        return path
    }

    internal suspend fun reindexRoot(diff: Difference) =
        withContext(Dispatchers.IO) {
            Log.d(
                RESOURCES_INDEX,
                "deleting ${diff.deleted.size} resources from RAM and previews"
            )
            diff.deleted.forEach {
                val id = metaByPath[it]!!.id
                pathById.remove(id)
                metaByPath.remove(it)
                previewStorage.forget(id)
            }

            val pathsToDelete = diff.deleted + diff.updated
            Log.d(
                RESOURCES_INDEX,
                "deleting ${pathsToDelete.size} resources from Room DB"
            )

            val chunks = pathsToDelete.chunked(512)
            Log.d(RESOURCES_INDEX, "splitting into ${chunks.size} chunks")
            chunks.forEach { paths ->
                dao.deletePaths(paths.map { it.toString() })
            }

            val newResources = mutableMapOf<Path, ResourceMeta>()
            val toInsert = diff.updated + diff.added

            val time1 = measureTimeMillis {
                toInsert.forEach { path ->
                    val result = ResourceMeta.fromPath(path)
                    result.onSuccess { meta ->
                        newResources[path] = meta
                        metaByPath[path] = meta
                        pathById[meta.id] = path
                    }
                    result.onFailure { e ->
                        _kindDetectFailedFlow.emit(path)
                        Log.d(
                            RESOURCES_INDEX,
                            "Could not detect kind for " +
                                path.absolutePathString()
                        )
                    }
                }
            }
            Log.d(
                RESOURCES_INDEX,
                "new resources metadata retrieved in ${time1}ms"
            )

            Log.d(
                RESOURCES_INDEX,
                "persisting ${newResources.size} updated resources"
            )
            persistResources(newResources)

            val time2 = measureTimeMillis {
                providePreviews()
            }
            Log.d(PREVIEWS, "previews provided in ${time2}ms")
        }

    internal suspend fun calculateDifference(): Difference =
        withContext(Dispatchers.IO) {
            val (present, absent) = metaByPath.keys.partition {
                Files.exists(it)
            }

            val updated = present
                .map { it to metaByPath[it]!! }
                .filter { (path, meta) ->
                    Files.getLastModifiedTime(path) > meta.modified
                }
                .map { (path, _) -> path }

            val added = listAllFiles(root).filter { file ->
                !metaByPath.containsKey(file)
            }

            Log.d(
                RESOURCES_INDEX,
                "${absent.size} absent, " +
                    "${updated.size} updated, ${added.size} added"
            )

            Difference(absent, updated, added)
        }

    internal suspend fun providePreviews() =
        withContext(Dispatchers.IO) {
            Log.d(
                PREVIEWS,
                "providing previews/thumbnails for ${metaByPath.size} resources"
            )

            supervisorScope {
                metaByPath.entries.map { (path: Path, meta: ResourceMeta) ->
                    async(Dispatchers.IO) {
                        previewStorage.generate(path, meta)
                    } to path
                }.forEach { (generateTask, path) ->
                    try {
                        generateTask.await()
                    } catch (e: Exception) {
                        Log.e(
                            PREVIEWS,
                            "Failed to generate preview/thumbnail for id ${
                            metaByPath[path]?.id
                            } ($path)"
                        )
                    }
                }
            }
        }

    internal suspend fun persistResources(resources: Map<Path, ResourceMeta>) =
        withContext(Dispatchers.IO) {
            Log.d(
                RESOURCES_INDEX,
                "persisting " +
                    "${resources.size} resources from root $root"
            )

            val roomResources = mutableListOf<Resource>()
            val roomExtra = mutableListOf<ResourceExtra>()

            resources.entries.toList()
                .forEach {
                    roomResources.add(Resource.fromMeta(it.value, root, it.key))
                    roomExtra.addAll(
                        GeneralKindFactory.toRoom(it.value.id, it.value.kind)
                    )
                }

            dao.insertResources(roomResources)
            dao.insertExtras(roomExtra)

            Log.d(RESOURCES_INDEX, "${resources.size} resources persisted")
        }

    override suspend fun updateResource(
        oldId: ResourceId,
        path: Path,
        newResource: ResourceMeta
    ) {
        metaByPath[path] = newResource
        pathById.remove(oldId)
        pathById[newResource.id] = path
        dao.updateResource(
            oldId, newResource.id, newResource.modified.toMillis(),
            newResource.size
        )
        dao.updateExtras(oldId, newResource.id)
    }

    companion object {

        internal fun loadResources(resources: List<ResourceWithExtra>):
            Map<Path, ResourceMeta> =
            resources
                .groupBy { room -> room.resource.path }
                .mapValues { (_, resources) ->
                    if (resources.size > 1) {
                        throw IllegalStateException(
                            "Index must not have" +
                                "several resources for the same path"
                        )
                    }
                    ResourceMeta.fromRoom(resources[0])
                }
                .mapKeys { (path, _) -> Paths.get(path) }

        internal suspend fun listAllFiles(folder: Path): List<Path> =
            withContext(Dispatchers.IO) {
                val (directories, files) = listChildren(folder)

                return@withContext files + directories.flatMap {
                    listAllFiles(it)
                }
            }
    }
}
