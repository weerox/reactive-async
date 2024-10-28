package com.phaller.rasync
package bench

import pool.HandlerPool
import cell.{ Cell, CellCompleter, FinalOutcome, NextOutcome, NoOutcome }
import lattice.Lattice
import lattice.lattices.PowerSetLattice
import lattice.DefaultKey

import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration._
import scala.util.{ Try, Success }

import java.util.concurrent.TimeoutException
import java.util.concurrent.CountDownLatch

import org.scalameter.api._
import org.scalameter.picklers.Implicits._

class Marker

object TreeBench extends Bench.LocalTime {

  // val depth = Gen.range("depth")(2, 20, 2)
  val depth = Gen.single("depth")(14)
  val degree = Gen.single("degree")(2)
  val tree = depth.cross(degree)

  val threadCount = 8

  performance of "Tree" in {
    using(tree) in {
      case (depth, degree) =>
        def build(height: Int, degree: Int)(implicit
          handler: HandlerPool[Set[Marker], Null],
          lattice: Lattice[Set[Marker]]): Cell[Set[Marker], Null] =
          if (height == 0) {
            CellCompleter.completed(Set(new Marker())).cell
          } else {
            val cells = Seq.fill(degree) { build(height - 1, degree) }
            val sum = CellCompleter()
            // NOTE This won't work, because a subset of the dependencies is removed somewhere.
            // This means that the length of the cells given to `when` and the length of the cells received by the callback is not the same.
            for (cell <- cells) {
              sum.cell.when(cell)(cells => {
                val set = cells
                  .map { case (cell, value) => value }
                  .foldLeft(Set[Marker]()) { (acc, x) =>
                    x match {
                      case Success(NextOutcome(value)) => acc union value
                      case Success(FinalOutcome(value)) => acc union value
                      case _ => acc
                    }
                  }

                if (!set.isEmpty) {
                  NextOutcome(set)
                } else {
                  NoOutcome
                }
              })
            }

            sum.cell
          }

        implicit val lattice: Lattice[Set[Marker]] = new PowerSetLattice[Marker]
        implicit val pool = new HandlerPool(DefaultKey[Set[Marker]], threadCount)
        val cell = build(depth, degree)

        val p = Promise[Unit]

        pool.onQuiescent { () =>
          p.success()
        }

        try {
          Await.ready(p.future, 300.seconds)
        } catch {
          case _: TimeoutException => println("timeout")
        }

        val result = cell.getResult().size
        val pow = math.pow(degree, depth)

        // println(result + " " + pow + " " + (result == pow))

        assert((result == pow))
    }
  }
}
