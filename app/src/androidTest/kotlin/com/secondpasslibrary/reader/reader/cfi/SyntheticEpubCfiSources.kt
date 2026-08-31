package com.secondpasslibrary.reader.reader.cfi

internal object SyntheticEpubCfiSources {
    const val PACKAGE_PATH = "OPS/package/package.opf"
    const val CHAPTER_ONE_PATH = "OPS/package/text/chapter-one.xhtml"
    const val CHAPTER_TWO_PATH = "OPS/shared/chapter-two.xhtml"
    const val NAVIGATION_PATH = "OPS/navigation/nav.xhtml"

    const val MIMETYPE = "application/epub+zip"

    private val restoreVectorMarkup = (1..72).joinToString("\n") { index ->
        """
        <p id="restore-vector-$index">
          Restore vector $index has distinctive durable text. Its deliberately repeated sentence
          gives the paginated renderer enough content to place this target on a stable column.
          The exact marker for this paragraph is restore-marker-$index.
        </p>
        """.trimIndent()
    }

    val containerXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0"
            xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$PACKAGE_PATH"
                media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
        """.trimIndent()

    val packageDocument =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf"
            version="3.0"
            unique-identifier="publication-id"
            xml:lang="en">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="publication-id">urn:uuid:second-pass-cfi-fixture</dc:identifier>
            <dc:title>Second Pass CFI Fixture</dc:title>
            <dc:language>en</dc:language>
            <meta property="dcterms:modified">2026-08-24T00:00:00Z</meta>
            <meta property="rendition:layout">reflowable</meta>
          </metadata>
          <manifest>
            <item id="navigation"
                href="../navigation/nav.xhtml"
                media-type="application/xhtml+xml"
                properties="nav"/>
            <item id="chapter-one"
                href="text/chapter-one.xhtml"
                media-type="application/xhtml+xml"/>
            <item id="chapter-two"
                href="../shared/chapter-two.xhtml"
                media-type="application/xhtml+xml"/>
          </manifest>
          <spine page-progression-direction="ltr">
            <itemref id="spine-chapter-one" idref="chapter-one"/>
            <itemref id="spine-chapter-two" idref="chapter-two"/>
          </spine>
        </package>
        """.trimIndent()

    val chapterOneXhtml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
          <head>
            <meta charset="UTF-8"/>
            <title>Chapter One</title>
          </head>
          <body>
            <section id="chapter-one-root">
              <h1 id="chapter-one-title">A Synthetic Beginning</h1>
              <p id="repeated-phrase">
                A repeated phrase marks this place. A repeated phrase marks another place.
              </p>
              <!-- A publication comment must not alter text offsets. -->
              <p id="inline-markup">Before <span id="nested-span">nested <em id="nested-emphasis">inline</em> markup</span> after.</p>
              <p id="line-break">First line<br id="durable-break"/>Second line</p>
              <p id="empty-content">Text before<span id="empty-span"></span>text after.</p>
              <p id="unicode-content">Café, naïve, Καλημέρα, 日本語, and emoji 😀 remain durable.</p>
              <p id="whitespace-content">
                Leading and   repeated whitespace surrounds <span>this phrase</span> safely.
              </p>
              <div id="runtime-insertion-anchor">Durable text after the runtime insertion point.</div>
            </section>
            <div id="r2-decoration-42"
                class="readium-runtime-lookalike publisher-copy"
                data-group="publisher-content"
                style="pointer-events: auto">
              Publisher content that only resembles a runtime decoration.
            </div>
            <p id="post-lookalike-target">Publisher content after the runtime lookalike remains addressable.</p>
          </body>
        </html>
        """.trimIndent()

    val chapterTwoXhtml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
          <head>
            <meta charset="UTF-8"/>
            <title>Chapter Two</title>
          </head>
          <body>
            <section id="chapter-two-root">
              <h1 id="chapter-two-title">A Relative Resource</h1>
              <p id="cross-spine-target">The second resource proves cross-spine CFI navigation.</p>
              <p id="second-repeated-phrase">A repeated phrase marks this place.</p>
              <p id="second-inline-range">A range crosses <span>several <strong>nested</strong> nodes</span> here.</p>
              $restoreVectorMarkup
            </section>
          </body>
        </html>
        """.trimIndent()

    val navigationXhtml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml"
            xmlns:epub="http://www.idpf.org/2007/ops"
            xml:lang="en"
            lang="en">
          <head>
            <meta charset="UTF-8"/>
            <title>Contents</title>
          </head>
          <body>
            <nav epub:type="toc" id="toc">
              <h1>Contents</h1>
              <ol>
                <li><a href="../package/text/chapter-one.xhtml">Chapter One</a></li>
                <li><a href="../shared/chapter-two.xhtml">Chapter Two</a></li>
              </ol>
            </nav>
          </body>
        </html>
        """.trimIndent()

    /** Adds a concrete Readium-owned decoration shape without changing publication source. */
    val injectRuntimeDecorationScript =
        """
        (() => {
          const runtimeNode = document.createElement("div");
          runtimeNode.id = "r2-decoration-9001";
          runtimeNode.setAttribute("data-group", "second-pass-test");
          runtimeNode.style.pointerEvents = "none";
          runtimeNode.textContent = "Transient renderer text";
          document.body.insertBefore(runtimeNode, document.body.firstChild);
          document.getElementById("empty-span").appendChild(document.createTextNode(""));
        })();
        """.trimIndent()
}
