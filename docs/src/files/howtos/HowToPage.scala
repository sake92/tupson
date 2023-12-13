package files.howtos

import utils.*
import Bundle.*

trait HowToPage extends DocPage {

  override def categoryPosts = List(
    Index,
    WeirdKeyNames,
    BackCompat
  )

  override def pageCategory = Some(Index.pageSettings.label)

  override def navbar = Some(Navbar.withActiveUrl(Index.ref))
}
