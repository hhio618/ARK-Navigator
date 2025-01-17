package space.taran.arknavigator.mvp.model.repo.kind

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import space.taran.arklib.loadLinkFile
import space.taran.arknavigator.mvp.model.repo.index.ResourceId
import java.nio.file.Path
import kotlin.io.path.pathString

object LinkKindFactory : ResourceKindFactory<ResourceKind.Link> {
    private const val JSON_FILE = "link.json"

    override val acceptedExtensions = setOf("link")
    override val acceptedKindCode = KindCode.LINK
    override val acceptedMimeTypes: Set<String>
        get() = setOf()

    override fun fromPath(path: Path): ResourceKind.Link {
        val linkJson = loadLinkFile(path.pathString)
        val link = Json.decodeFromString(JsonLink.serializer(), linkJson)

        return ResourceKind.Link(link.title, link.desc, link.url)
    }

    override fun fromRoom(extras: Map<MetaExtraTag, String>): ResourceKind.Link =
        ResourceKind.Link(
            extras[MetaExtraTag.TITLE],
            extras[MetaExtraTag.DESCRIPTION],
            extras[MetaExtraTag.URL]
        )

    override fun toRoom(
        id: ResourceId,
        kind: ResourceKind.Link
    ): Map<MetaExtraTag, String?> = mapOf(
        MetaExtraTag.URL to kind.url,
        MetaExtraTag.TITLE to kind.title,
        MetaExtraTag.DURATION to kind.description
    )
}

@Serializable
private data class JsonLink(val url: String, val title: String, val desc: String)
