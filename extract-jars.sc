//> using lib "com.lihaoyi::os-lib:0.7.8"

/** Look at a file with one jar per line and for each jar run jar -xf and put
  * the result in a folder
  *
  * EG: `scala-cli extract-jars.sc -- "META-INF/smithy"`
  */

val filter: Option[String] = args.headOption

val lines = os.read(os.pwd / "data" / "jars.txt").split("\n")
val output = os.pwd / "result" / "extract-jars"
os.remove.all(output)
os.makeDir.all(output)
lines.foreach { f =>
  val name = f.split("/").last
  val dest = output / name
  os.makeDir(dest)
  val cmd = Seq("jar", "-xf", f) ++ filter.toSeq
  os.proc(cmd).call(cwd = dest)
}
