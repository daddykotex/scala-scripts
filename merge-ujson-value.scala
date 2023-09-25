/** This snippet of code is useful to merge two json value together.
  *
  * If two values are:
  *   - js object, they are merged
  *   - js array, they are concatenated
  *   - if one key has different types in the two js objects, we keep the left
  *     hand side
  *   - if not an array or an object, we keep the left hand side
  */
//> using lib "com.lihaoyi::upickle:3.1.3"

import upickle.default._

object Main extends App {
  import TestCases._
  // format: off
  val cases = Seq(
    ti1    ->    ti1    ->   te1,
    ti2a   ->    ti2b   ->   te2,
    ti3a   ->    ti3b   ->   te3,
    ti4a   ->    ti4b   ->   te4
  )
  // format: on

  def merge(v1: ujson.Value, v2: ujson.Value): ujson.Value = {
    (v1, v2) match {
      case (ujson.Obj(obj1), ujson.Obj(obj2)) =>
        val result = obj2.foldLeft(obj1.toMap) {
          case (elements, (key, value2)) =>
            val value = elements.get(key) match {
              case None =>
                value2
              case Some(value1) =>
                merge(value1, value2)
            }
            elements.updated(key, value)
        }
        ujson.Obj.from(result)
      case (arr1: ujson.Arr, arr2: ujson.Arr) =>
        ujson.Arr(arr1.arr ++ arr2.arr)
      case (_, _) => v1
    }
  }

  cases.foreach { case ((tia, tib), te) =>
    val actual = merge(read[ujson.Value](tia), read[ujson.Value](tib)).render()
    require(
      actual.trim() == te.trim(),
      s"""|
          |Actual:   $actual
          |===
          |Expected: $te
          |""".stripMargin
    )
  }
}

object TestCases {
  val ti1 = """|{}
               |""".stripMargin
  val te1 = """|{}
               |""".stripMargin

  val ti2a = """|{"bar":"foo"}
               |""".stripMargin
  val ti2b = """|{"baz":"bar"}
                |""".stripMargin
  val te2 = """|{"bar":"foo","baz":"bar"}
               |""".stripMargin

  val ti3a = """|{"bar":["foo"]}
                |""".stripMargin
  val ti3b = """|{"bar":[2]}
                |""".stripMargin
  val te3 = """|{"bar":["foo",2]}
               |""".stripMargin

  val ti4a = """|{"maven":{"dependencies":["foo"]}}
                |""".stripMargin
  val ti4b = """|{"maven":{"dependencies":["foo2"]}}
                |""".stripMargin
  val te4 = """|{"maven":{"dependencies":["foo","foo2"]}}
               |""".stripMargin

  // val ti1 = """|{}
  //              |""".stripMargin
  // val ti1 = """|{}
  //              |""".stripMargin
  // val te1 = """|{}
  //              |""".stripMargin
}
