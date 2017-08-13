package com.example

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps

class BasicImplicitTest extends TestKit(ActorSystem("Doubles-actor"))
  with WordSpecLike
  with Matchers
  with ImplicitSender
  with Eventually {

  "Doubler" should {
    "double the input" in {
      val d2 = system.actorOf(Doubler.props)
      within(300 milliseconds) {
        d2 ! 2
        expectMsg(4)
      }
    }
  }
}
