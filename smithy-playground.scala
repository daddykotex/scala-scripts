//> using scala "2.13.10"
//> using lib "software.amazon.smithy:smithy-model:1.27.0"

import software.amazon.smithy.model.Model
import scala.jdk.CollectionConverters._
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.DefaultTrait

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
          |@range(min: 1)
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
  ) // {}
  val indirect = model.expectShape(ShapeId.from("atest#IndirectInt$member"))
  println(
    indirect.getAllTraits()
  ) // {smithy.api#default=Trait `smithy.api#default`, defined at defaults.smithy [10, 3]}
  println(
    indirect.getTrait(classOf[DefaultTrait]).get().toNode()
  ) // 0
}
