//> using scala "2.13.10"
//> using lib "software.amazon.smithy:smithy-model:1.27.0"

import software.amazon.smithy.model.Model
import scala.jdk.CollectionConverters._
import software.amazon.smithy.model.shapes.ShapeId

object Main extends App {
  val model = Model
    .assembler()
    .addUnparsedModel(
      "defaults.smithy",
      s"""|namespace atest
          |
          |structure DirectInt {
          |  member: Integer
          |}
          |
          |integer MyInt
          |structure IndirectInt {
          |  member: MyInt
          |}
          |""".stripMargin
    )
    .assemble()
    .unwrap()
  println(
    model.expectShape(ShapeId.from("atest#DirectInt$member")).getAllTraits()
  )
  println(
    model.expectShape(ShapeId.from("atest#IndirectInt$member")).getAllTraits()
  )
}
