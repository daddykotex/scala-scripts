//> using scala "3.3.1"
//> using lib "software.amazon.smithy:smithy-model:1.42.0"

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.AbstractShapeBuilder
import software.amazon.smithy.model.shapes.IntegerShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape

object Main extends App {
  def test(id: String): Shape = {
    val builder: AbstractShapeBuilder[_, _] = id match {
      case "test"  => StringShape.builder()
      case "other" => IntegerShape.builder().addMixin(null)
      case _       => IntegerShape.builder()
    }
    // the following works
    builder.id(id).build()

    // the following two do not work
    // builder.id(id)
    // builder.build()
  }
}
