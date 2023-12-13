package files.philosophy

import utils.Bundle.*
import utils.Consts

object Index extends PhilosophyPage {

  override def pageSettings =
    super.pageSettings.withTitle("Philosophy")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    "Why not implicit config",
    s"""

    Circe for example is using an `implicit` configuration, where you can tell it to map camelCase to snake_case etc.  
    ${Consts.ProjectName} deliberately avoids this in order to simplify things.  
    It is really hard to find which implicit config is being applied where, and you need to test your codecs.. meh

    Benefits of ${Consts.ProjectName}'s simplistic approach:
    - your code is easy for "grep" / Ctrl+F
    - no mismatch between serialized version and your code
    - your internal/core models are separate from JSON, as they should be
    - mapping between models is explicit
  
    """.md
  )
}
