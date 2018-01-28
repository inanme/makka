package com.example

import java.util.UUID

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.EventFilter
import org.scalatest.{FreeSpec, Matchers}

class DirectiveSpec extends FreeSpec with Matchers with ScalatestRouteTest {
  override def testConfigSource: String =
    s"""akka {
       | test.filter-leeway = 3s
       | loggers = ["akka.testkit.TestEventListener", "akka.event.slf4j.Slf4jLogger"]
       | }
       | """.stripMargin

  def generateUuid: Directive1[UUID] = {
    //provide(UUID.randomUUID)
    extract(ctx => UUID.randomUUID)
  }

  def generateUuidIfNotPresent: Directive1[UUID] = optionalHeaderValueByName("my-uuid") flatMap {
    case Some(uuid) ⇒ provide(UUID.fromString(uuid))
    case None ⇒
      val uuid = UUID.randomUUID()
      val uuidHeader = RawHeader("my-uuid", uuid.toString)
      mapRequest(r ⇒ r.withHeaders(uuidHeader +: r.headers)) &
      provide(uuid)
  }

  "The UUID Directive" - {
    //http://blog.michaelhamrah.com/2014/05/spray-directives-creating-your-own-simple-directive/

    "can generate a UUID" in {
      Get() ~> generateUuid { uuid => complete(uuid.toString) } ~> check {
        responseAs[String].length shouldBe 36
      }
    }
    "will generate a new UUID as header is not present" in {
      Get() ~> generateUuidIfNotPresent { uuid => complete(uuid.toString) } ~> check {
        responseAs[String].length shouldBe 36
      }
    }
    "will generate uuid if one is present in the request headers" in {
      val myUuid = UUID.randomUUID()
      Get() ~> addHeader("my-uuid", myUuid.toString) ~> generateUuidIfNotPresent { uuid => complete(uuid.toString) } ~> check {
        responseAs[String] shouldBe myUuid.toString
      }
    }
    "can extract the same uuid twice per request" in {
      var uuid1: String = ""
      var uuid2: String = ""
      Get() ~> generateUuidIfNotPresent {
        uuid => {
          uuid1 = uuid.toString
          generateUuidIfNotPresent { another =>
            uuid2 = another.toString
            complete("")
          }
        }
      } ~> check {
        //fails
        uuid1 shouldEqual uuid2
      }
    }
    "will generate different UUID per request" in {
      //like the runtime, instantiate route once
      val uuidRoute: Route = generateUuid { uuid => complete(uuid.toString) }
      var uuid1: String = ""
      var uuid2: String = ""
      Get() ~> uuidRoute ~> check {
        responseAs[String].length shouldBe 36
        uuid1 = responseAs[String]
      }
      Get() ~> uuidRoute ~> check {
        responseAs[String].length shouldBe 36
        uuid2 = responseAs[String]
      }
      //fails!
      uuid1 shouldNot equal(uuid2)
    }
  }
  "Loggers" - {
    "should log" in {
      case class SomeException() extends RuntimeException
      val route: Route = extractLog { log =>
        log.error("joe")
        complete("ok")
      }
      EventFilter.error(message = "joe", occurrences = 1) intercept {
        Get() ~> route ~> check {
          responseAs[String] shouldBe "ok"
        }
      }
    }
  }
}