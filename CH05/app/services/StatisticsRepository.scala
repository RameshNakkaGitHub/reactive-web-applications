package services

import org.joda.time.DateTime
import play.modules.reactivemongo._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson._
import play.api.Play.current

import scala.concurrent.{ExecutionContext, Future}

trait StatisticsRepository {

  def storeCounts(counts: StoredCounts)(implicit ec: ExecutionContext): Future[Unit]

  def retrieveLatestCounts(userName: String)(implicit ec: ExecutionContext): Future[StoredCounts]

}

case class StoredCounts(when: DateTime, userName: String, followersCount: Long, friendsCount: Long)

object StoredCounts {
   implicit object UserCountsReader extends BSONDocumentReader[StoredCounts] with BSONDocumentWriter[StoredCounts] {
    override def read(bson: BSONDocument): StoredCounts = {
      val when = bson.getAs[BSONDateTime]("when").map(t => new DateTime(t.value)).get
      val userName = bson.getAs[String]("userName").get
      val followersCount = bson.getAs[Long]("followersCount").get
      val friendsCount = bson.getAs[Long]("friendsCount").get
      StoredCounts(when, userName, followersCount, friendsCount)
    }

    override def write(t: StoredCounts): BSONDocument = BSONDocument(
      "when" -> BSONDateTime(t.when.getMillis),
      "userName" -> t.userName,
      "followersCount" -> t.followersCount,
      "friendsCount" -> t.friendsCount
    )
  }
}

class MongoStatisticsRepository extends StatisticsRepository {

  private val StatisticsCollection = "UserStatistics"

  private lazy val collection = ReactiveMongoPlugin.db.collection[BSONCollection](StatisticsCollection)

  override def storeCounts(counts: StoredCounts)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.save(counts).map { lastError =>
      if(lastError.inError) {
        throw CountStorageException(counts)
      }
    }
  }

  override def retrieveLatestCounts(userName: String)(implicit ec: ExecutionContext): Future[StoredCounts] = {
    val query = BSONDocument("userName" -> userName)
    val order = BSONDocument("_id" -> -1)
    collection
      .find(query)
      .sort(order)
      .one[StoredCounts]
      .map { counts => counts getOrElse StoredCounts(DateTime.now, userName, 0, 0) }
  }
}

case class CountStorageException(counts: StoredCounts) extends RuntimeException