package com.phaller.rasync
package bench

import pool.HandlerPool
import cell.{Cell, CellCompleter, FinalOutcome, NextOutcome, NoOutcome}
import lattice.Lattice
import lattice.lattices.NaturalNumberLattice
import lattice.DefaultKey

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.{Try, Success}

import java.util.concurrent.TimeoutException
import java.util.concurrent.CountDownLatch

import org.scalameter.api._
import org.scalameter.picklers.Implicits._

object GraphBench extends Bench.LocalTime {
  val n = Gen.single("nodes")(10000)
  val p = n.map(n => ((1 + 2.0) * math.log(n)) / n)
  val graph = n.cross(p)

  val threadCount = 16

  performance of "Graph" in {
    using(graph) in { case (n, p) =>
      implicit val lattice: Lattice[Int] = new NaturalNumberLattice
      implicit val pool = new HandlerPool(DefaultKey[Int], threadCount)

      val random = new scala.util.Random(20001026)

      var count = 0
      val runs = new java.util.concurrent.atomic.AtomicInteger()

      val nodes =
        List.fill(n)(
          CellCompleter[Int, Null](_ =>
            NextOutcome(scala.util.Random.between(0, 99))
          )
        )

      for {
        i <- nodes
        j <- nodes
      } {
        if (random.nextDouble() < p) {
          count += 1
          i.cell.when(j.cell)(cells => {
            runs.incrementAndGet()
            cells.head._2 match {
              case Success(NextOutcome(value))  => NextOutcome(value)
              case Success(FinalOutcome(value)) => NextOutcome(value)
              case _                            => NoOutcome
            }
          })
        }
      }

      val promise = Promise[Unit]

      pool.onQuiescent { () =>
        promise.success()
      }

      try {
        Await.ready(promise.future, 300.seconds)
      } catch {
        case _: TimeoutException => println("timeout")
      }

      println(s"count: ${count}")
      println(s"runs: ${runs.get()}")
    }
  }
}
