package files

import utils.*
import Bundle.*, Tags.*
import ba.sake.hepek.html.statik.BlogPostPage

object Index extends DocStaticPage {

  override def pageSettings = super.pageSettings
    .withTitle(Consts.ProjectName)

  override def navbar = Some(Navbar)

  override def pageContent = Grid.row(
    h1(Consts.ProjectName),
    s"""
    ${Consts.ProjectName} is a stupid simple, minimalistic, Scala 3 library for writing and reading JSON.  
    It only does `String` <=> `T` conversions, no streaming.

    ---
    Site map:
    """.md,
    div(cls := "site-map")(
      siteMap.md
    )
  )

  private def siteMap =
    Index.staticSiteSettings.mainPages
      .map {
        case mp: BlogPostPage =>
          val subPages = mp.categoryPosts
            .drop(1) // skip Index ..
            .map { cp =>
              s"  - [${cp.pageSettings.label}](${cp.ref})"
            }
            .mkString("\n")
          s"- [${mp.pageSettings.label}](${mp.ref})\n" + subPages
        case _ => ""
      }
      .mkString("\n")
}
