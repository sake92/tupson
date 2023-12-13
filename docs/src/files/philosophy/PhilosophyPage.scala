package files.philosophy

import utils.*
import Bundle.*

trait PhilosophyPage extends DocPage {

  override def categoryPosts = List(
    Index
  )

  override def pageCategory = Some(Index.pageSettings.label)

  override def navbar = Some(Navbar.withActiveUrl(Index.ref))
}
