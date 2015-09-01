package no.mesan.hipchatparse.rooms

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import no.mesan.hipchatparse.{Breakdown, TaskDone}
import no.mesan.hipchatparse.rooms.RoomParser.MakeRoom
import no.mesan.hipchatparse.rooms.RoomWriter.RoomDone
import no.mesan.hipchatparse.utils.{FileIO, NameHelper}

/** Scans room directory. */
class RoomDirReader(master: ActorRef, fileReader: ActorRef) extends Actor
  with ActorLogging with NameHelper {
  import no.mesan.hipchatparse.rooms.RoomDirReader.{BuildRooms, FoundRoom, LastRoom}
  import no.mesan.hipchatparse.rooms.RoomFileReader.ReadRoom

  override def receive: Receive = LoggingReceive {
    case BuildRooms(baseDir) =>
      val dirs= FileIO.scanDir(baseDir).dirs.sorted
      if (dirs.isEmpty) {
        master ! Breakdown(s"no rooms in directory $baseDir")
      }
      else {
        for (dir <- dirs) {
          master ! FoundRoom(stripPath(dir))
          fileReader ! ReadRoom(dir)
        }
      }
      master ! LastRoom
      master ! TaskDone("listing rooms")
  }
}

object RoomDirReader {
  /** Start scanning room root directory. */
  case class BuildRooms(dirName: String)
  /** Report a new room. */
  case class FoundRoom(roomName: String)
  /** Report the room scan being over. */
  case object LastRoom

  /** "Constructor" */
  def props(master: ActorRef, fileReader: ActorRef) = Props(new RoomDirReader(master, fileReader))
}

/** Reads all files in a room. */
class RoomFileReader(master: ActorRef, roomParser: ActorRef) extends Actor
  with ActorLogging with NameHelper {
  import no.mesan.hipchatparse.rooms.RoomFileReader.ReadRoom

  override def receive: Receive = LoggingReceive {
    case ReadRoom(dir) =>
      val roomName= stripPath(dir)
      val files= FileIO.scanDir(dir).files.sorted.reverse
      if (files.isEmpty) {
        log.warning(s"no files in directory $dir")
        master ! RoomDone(roomName, 0)
      }
      else {
        var jsonList= List.empty[String]
        for (file <- files) {
          val contents= FileIO.fromFile(file)
          contents.foreach(s=> jsonList= s.replaceAll("[\r\u000A]", "") :: jsonList)
        }
        roomParser ! MakeRoom(roomName, jsonList)
        master ! TaskDone(s"read room $roomName")
      }
  }
}

object RoomFileReader {
  /** Start reading files for a room. */
  case class ReadRoom(roomName: String)

  /** "Constructor" */
  def props(master: ActorRef, roomParser: ActorRef) = Props(new RoomFileReader(master, roomParser))
}
