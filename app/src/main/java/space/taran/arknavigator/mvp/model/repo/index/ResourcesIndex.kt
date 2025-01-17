package space.taran.arknavigator.mvp.model.repo.index

import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

interface ResourcesIndex {

    val kindDetectFailedFlow: Flow<Path>

    // we pass all known resource ids to a storage because
    // 1) any storage exists globally
    // 2) we maintain only 1 storage per root
    // 3) every storage is initialized with resource ids
    suspend fun listIds(prefix: Path?): Set<ResourceId> =
        listResources(prefix).map { it.id }.toSet()

    suspend fun listResources(prefix: Path?): Set<ResourceMeta>

    suspend fun listAllIds(): Set<ResourceId> = listIds(null)

    // whenever we have an id, we assume that we have this id in the index
    // we must load/calculate all necessary ids before we load presenters
    suspend fun getPath(id: ResourceId): Path

    suspend fun getMeta(id: ResourceId): ResourceMeta

    suspend fun reindex()

    suspend fun remove(id: ResourceId): Path

    suspend fun updateResource(
        oldId: ResourceId,
        path: Path,
        newResource: ResourceMeta
    )
}
