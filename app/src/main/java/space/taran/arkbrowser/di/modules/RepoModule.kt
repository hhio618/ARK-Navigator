package space.taran.arkbrowser.di.modules

import space.taran.arkbrowser.mvp.model.entity.room.db.Database
import space.taran.arkbrowser.mvp.model.repo.FilesRepo
import space.taran.arkbrowser.mvp.model.repo.RoomRepo
import space.taran.arkbrowser.mvp.model.repo.SynchronizeRepo
import space.taran.arkbrowser.ui.App
import space.taran.arkbrowser.ui.file.DocumentDataSource
import space.taran.arkbrowser.ui.file.FileDataSource
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class RepoModule {
    @Singleton
    @Provides
    fun roomRepo(database: Database): RoomRepo {
        return RoomRepo(database)
    }

    @Singleton
    @Provides
    fun fileProvider3(app: App): FileDataSource {
        return FileDataSource(app)
    }

    @Singleton
    @Provides
    fun documentProvider(app: App, fileDataSource: FileDataSource): DocumentDataSource {
        return DocumentDataSource(app, fileDataSource)
    }

    @Singleton
    @Provides
    fun filesRepo3(fileDataSource: FileDataSource, documentDataSource: DocumentDataSource): FilesRepo {
        return FilesRepo(fileDataSource, documentDataSource)
    }

    @Singleton
    @Provides
    fun syncManager(roomRepo: RoomRepo, filesRepo: FilesRepo) = SynchronizeRepo(roomRepo, filesRepo)
}