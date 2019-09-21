package com.gilcu2.processing

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Column, DataFrame, Row, SparkSession}
import java.sql.Timestamp

object Processing {

  val userIdField = "userId"
  val timeField = "timeStamp"
  val artistIdField = "artistId"
  val artistNameField = "artistName"
  val songIdField = "songId"
  val songNameField = "songName"
  val timeStampsField = "timeStamps"
  val artistNamesField = "artistNames"
  val songNamesField = "songNames"
  val userTracksField = "userTracks"
  val userSessionsField = "userSessions"

  val fields = Array(userIdField, timeField, artistIdField, artistNameField, songIdField, songNameField)

  implicit def ordered: Ordering[Timestamp] = new Ordering[Timestamp] {
    def compare(x: Timestamp, y: Timestamp): Int = x compareTo y
  }

  def prepareData(df: DataFrame)(implicit spark: SparkSession): DataFrame =
    df
      .withColumn(userIdField, trim(col("_c0")))
      .withColumn(timeField, trim(col("_c1")))
      .withColumn(artistIdField, trim(col("_c2")))
      .withColumn(artistNameField, trim(col("_c3")))
      .withColumn(songIdField, trim(col("_c4")))
      .withColumn(songNameField, trim(col("_c5")))
      .select(userIdField, timeField, artistIdField, artistNameField, songIdField, songNameField)

  val computeUserSessions = udf[Seq[Seq[(Timestamp, String, String)]], Seq[Timestamp], Seq[String], Seq[String]](
    (times, artists, songs) => {
      val joinedSorted = times.zip(artists).zip(songs).map(t => (t._1._1, t._1._2, t._2))
        .sortBy(_._1)

      val sessions = scala.collection.mutable.ListBuffer[Seq[(Timestamp, String, String)]]()
      var session = scala.collection.mutable.ListBuffer[(Timestamp, String, String)]()
      session.append(joinedSorted.head)
      var lastMinutes = joinedSorted.head._1.getTime / 60000
      joinedSorted.tail.foreach(t => {
        val minutes = t._1.getTime / 60000
        if (minutes - lastMinutes < 20)
          session.append(t)
        else {
          sessions.append(session)
          session = scala.collection.mutable.ListBuffer(t)
        }
        lastMinutes = minutes
      })
      sessions.append(session)
      sessions
    })


  def computeUserSessions(tracks: DataFrame)(implicit spark: SparkSession): DataFrame = {

    tracks.printSchema()

    val userSessions = tracks
      .groupBy(userIdField)
      .agg(
        collect_list(timeField).as(timeStampsField),
        collect_list(artistNameField).as(artistNamesField),
        collect_list(songNameField).as(songNamesField)
      )
      .withColumn(userTracksField, computeUserSessions(col(timeStampsField),
        col(artistNamesField), col(songNamesField)))
      .select(col(userIdField), explode(col(userTracksField)).as(userSessionsField))

    userSessions
  }



  def computeTopFromLongestSessions(tracks: DataFrame, top: Int, sessions: Int)(implicit spark: SparkSession): DataFrame = {
    spark.emptyDataFrame
  }

}
