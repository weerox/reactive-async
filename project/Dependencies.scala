import sbt._

object Dependencies {
  lazy val munit = "org.scalameta" %% "munit" % "1.0.0" % "test"
  lazy val opalCommon = ("de.opal-project" %% "common" % "5.0.0").cross(CrossVersion.for3Use2_13)
  lazy val opalTAC = ("de.opal-project" %% "three-address-code" % "5.0.0" % "test").cross(CrossVersion.for3Use2_13)
  lazy val scalaMeter = ("com.storm-enroute" %% "scalameter" % "0.21").cross(CrossVersion.for3Use2_13)
}
