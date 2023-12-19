//> using scala "3.3.1"
//> using lib "software.amazon.smithy:smithy-model:1.42.0"

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.AbstractShapeBuilder
import software.amazon.smithy.model.shapes.IntegerShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape

/*

Compiling project (Scala 3.3.1, JVM)
[error] ./smithy-playground.scala:12:16
[error] Type argument  >: software.amazon.smithy.model.shapes.StringShape#Builder &
[error]   software.amazon.smithy.model.shapes.IntegerShape#Builder² <:
[error]   software.amazon.smithy.model.shapes.StringShape#Builder |
[error]   software.amazon.smithy.model.shapes.IntegerShape#Builder² does not overlap with upper bound software.amazon.smithy.model.shapes.AbstractShapeBuilder[
[error]   software.amazon.smithy.model.shapes.AbstractShapeBuilder[
[error]     ?
[error]        >: software.amazon.smithy.model.shapes.StringShape#Builder &
[error]         software.amazon.smithy.model.shapes.IntegerShape#Builder² <:
[error]         software.amazon.smithy.model.shapes.StringShape#Builder |
[error]         software.amazon.smithy.model.shapes.IntegerShape#Builder²
[error]     ,
[error]   ?
[error]      >: software.amazon.smithy.model.shapes.StringShape &
[error]       software.amazon.smithy.model.shapes.IntegerShape <:
[error]       software.amazon.smithy.model.shapes.StringShape |
[error]       software.amazon.smithy.model.shapes.IntegerShape
[error]   ]#B,
[error]   software.amazon.smithy.model.shapes.AbstractShapeBuilder[
[error]     ?
[error]        >: software.amazon.smithy.model.shapes.StringShape#Builder &
[error]         software.amazon.smithy.model.shapes.IntegerShape#Builder² <:
[error]         software.amazon.smithy.model.shapes.StringShape#Builder |
[error]         software.amazon.smithy.model.shapes.IntegerShape#Builder²
[error]     ,
[error]   ?
[error]      >: software.amazon.smithy.model.shapes.StringShape &
[error]       software.amazon.smithy.model.shapes.IntegerShape <:
[error]       software.amazon.smithy.model.shapes.StringShape |
[error]       software.amazon.smithy.model.shapes.IntegerShape
[error]   ]#S
[error] ] in inferred type
[error]   software.amazon.smithy.model.shapes.AbstractShapeBuilder[
[error]     ?
[error]        >: software.amazon.smithy.model.shapes.StringShape#Builder &
[error]         software.amazon.smithy.model.shapes.IntegerShape#Builder <:
[error]         software.amazon.smithy.model.shapes.StringShape#Builder |
[error]         software.amazon.smithy.model.shapes.IntegerShape#Builder
[error]     ,
[error]   ?
[error]      >: software.amazon.smithy.model.shapes.StringShape &
[error]       software.amazon.smithy.model.shapes.IntegerShape <:
[error]       software.amazon.smithy.model.shapes.StringShape |
[error]       software.amazon.smithy.model.shapes.IntegerShape
[error]   ]
[error]
[error]
[error] where:    Builder  is a class in object StringShape
[error]           Builder² is a class in object IntegerShape
[error]     val builder =
[error]                ^
Error compiling project (Scala 3.3.1, JVM)
Compilation failed
 */
object Main extends App {
  def test(id: String): Shape = {
    val builder
        : AbstractShapeBuilder[_ <: AbstractShapeBuilder[_, _], _ <: Shape] =
      id match {
        case "test" => StringShape.builder()
        case _      => IntegerShape.builder()
      }
    builder.id(id)
    builder.build()
  }
}
