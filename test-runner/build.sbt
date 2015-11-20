import sbt.complete.Parsers._

organization := "com.typesafe.spark"
name := "mesos-spark-integration-tests"
version := "0.1.0"
scalaVersion := "2.10.5"

//default Spark version
val sparkVersion = "1.5.1"

val sparkHome = SettingKey[Option[String]]("spark-home", "the value of the variable 'spark.home'")

def assemblyJarPath(sparkHome: String): String = {
  val sparkHomeFile = file(sparkHome)
  val assemblyJarFilter = (sparkHomeFile / "lib") * "spark-assembly-*.jar"
  val jarPath = assemblyJarFilter.getPaths.headOption.getOrElse(sys.error(s"No spark assembly jar found under ${sparkHome}/lib"))
  s"file:///${jarPath}"
}

def addSparkDependencies(sparkHome: Option[String]) = if (sparkHome.isDefined) {
    Seq(
      "org.apache.spark" % "spark-assembly" % "spark-home-version" % "provided" from(assemblyJarPath(sparkHome.get)))
  } else {
   Seq(
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided" withSources(),
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided" withSources(),
    "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided" withSources(),
    "org.apache.spark" %% "spark-sql" % sparkVersion % "provided" withSources())
  }


sparkHome := sys.props.get("spark.home")

libraryDependencies ++= addSparkDependencies(sparkHome.value) ++ Seq(
 "org.apache.hadoop" % "hadoop-client"    % "2.6.1" excludeAll(
   ExclusionRule(organization = "commons-beanutils"),
   ExclusionRule(organization = "org.apache.hadoop", name ="hadoop-yarn-api")),
 "org.scalatest"     %% "scalatest"       % "2.2.4",
 "com.typesafe"      %  "config"          % "1.2.1"
)


val mit = inputKey[Unit]("Runs spark/mesos integration tests.")

mainClass := Some("org.typesafe.spark.mesos.framework.runners.MesosIntegrationTestRunner")

//invoking inputtasks is weird in sbt. TODO simplify this
def runMainInCompile(sparkHome: String,
                     mesosMasterUrl: String,
                     applicationJar: String) = Def.taskDyn {

 val main = s"  ${mainClass.value.get} $sparkHome $mesosMasterUrl $applicationJar"
 (runMain in Compile).toTask(main)
}

mit := Def.inputTaskDyn {
 val args: Seq[String] = spaceDelimited("<arg>").parsed

 if(args.size != 2) {
  val log = streams.value.log
  log.error("Please provide all the args: <spark-home> <mesos-master-url>")
  sys.error("failed")
 }
 args foreach println
 //find the spark-submit shell script
 val sparkHome = args(0)
 val mesosMasterUrl = args(1)

 //depends on assembly task to package the current project
 val output = assembly.value

 //run the main with args
 runMainInCompile(sparkHome, mesosMasterUrl, output.getAbsolutePath)

}.evaluated
