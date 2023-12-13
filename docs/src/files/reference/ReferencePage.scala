package files.reference

import utils.*
import Bundle.*

trait ReferencePage extends DocPage {

  override def categoryPosts = List(Index, Options, Collections, Maps, SimpleEnums, CaseClasses, SumTypes, DynamicJson)

  override def pageCategory = Some(Index.pageSettings.label)

  override def navbar = Some(Navbar.withActiveUrl(Index.ref))
}
