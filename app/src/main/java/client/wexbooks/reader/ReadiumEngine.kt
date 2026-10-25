package client.wexbooks.reader

import android.content.Context
import java.io.File
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumEngine(context: Context) {
    private val httpClient = DefaultHttpClient()
    private val assets = AssetRetriever(context.contentResolver, httpClient)
    private val opener =
        PublicationOpener(
            publicationParser =
                DefaultPublicationParser(
                    context,
                    assetRetriever = assets,
                    httpClient = httpClient,
                    pdfFactory = null,
                )
        )

    suspend fun open(file: File): Publication {
        val asset =
            assets.retrieve(file).getOrElse {
                throw BookImportException("Файл не похож на корректную EPUB-книгу.")
            }
        val publication =
            opener.open(asset, allowUserInteraction = false).getOrElse {
                asset.close()
                throw BookImportException("Структура EPUB повреждена. Попробуйте другое издание.")
            }
        if (
            !publication.conformsTo(Publication.Profile.EPUB) ||
                (publication.metadata.layout != null &&
                    publication.metadata.layout != Layout.REFLOWABLE)
        ) {
            publication.close()
            throw BookImportException("Выберите EPUB с обычным перетекающим текстом.")
        }
        return publication
    }
}

class BookImportException(message: String) : Exception(message)
