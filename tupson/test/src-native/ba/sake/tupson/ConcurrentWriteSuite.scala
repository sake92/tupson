package ba.sake.tupson

import java.util.concurrent.ConcurrentLinkedQueue
import ba.sake.tupson.{*, given}

class ConcurrentWriteSuite extends munit.FunSuite {
  import ConcurrentWriteSuite.*

  test("concurrent serialization preserves large nested payloads") {
    val failures = new ConcurrentLinkedQueue[Throwable]()
    val threads = Vector.tabulate(32) { worker =>
      val thread = new Thread(() => {
        try {
          val items = List.tabulate(50) { index =>
            Item(worker * 50 + index, "Čaršija\n\"\\" * 20, List("a", "b"), 9876543210L + index)
          }
          val payload = Payload(items, worker)
          for {
            iteration <- 0 until 20
            spaces <- List(0, 2)
            sort <- List(false, true)
          } {
            val json = payload.toJson(spaces = spaces, sort = sort)
            assertEquals(json.parseJson[Payload], payload)
          }
        } catch {
          case e: Throwable =>
            failures.add(e)
            ()
        }
      })
      thread.setDaemon(true)
      thread
    }
    threads.foreach(_.start())
    threads.foreach { thread =>
      thread.join(30000)
      assert(!thread.isAlive, "serialization worker did not finish")
    }
    if !failures.isEmpty then throw failures.peek()
  }
}

private object ConcurrentWriteSuite {
  case class Item(id: Int, name: String, tags: List[String], price: Long) derives JsonRW
  case class Payload(items: List[Item], worker: Int) derives JsonRW
}
