package files.tutorials

import utils.*
import Bundle.*

trait TutorialPage extends DocPage {

  override def categoryPosts = List(
    Index,
    Writing,
    Parsing
  )

  override def pageCategory = Some(Index.pageSettings.label)

  override def navbar = Some(Navbar.withActiveUrl(Index.ref))
}
