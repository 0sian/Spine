package app.spine.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TitlesTest {
    @Test fun dropsAuthorSeriesNumberAndBracketedTags() = assertEquals(
        "The Real Title",
        Titles.clean("Lastname, F. - Series Name 03 - The Real Title [some-site.com]", "Lastname, F."),
    )
    @Test fun dropsAYearInBrackets() = assertEquals("The Real Title", Titles.clean("The Real Title (2019)"))
    @Test fun dropsAHostnameLeftOnTheEnd() = assertEquals("The Hobbit", Titles.clean("The Hobbit - example-site.com"))
    @Test fun dropsAHostnameAfterAnUnderscoreFileName() = assertEquals("The Hobbit", Titles.clean("The_Hobbit_example-site.com"))
    @Test fun dropsATruncatedBracketTagFromALongFileName() = assertEquals("A Long Book Title", Titles.clean("A Long Book Title (Most_ (some-site.sk, other-site.sk"))
    @Test fun dropsEditionNoise() = assertEquals("Watchmen", Titles.clean("Watchmen: The Deluxe Edition"))
    @Test fun leavesAPlainTitleAlone() = assertEquals("Pride and Prejudice", Titles.clean("Pride and Prejudice"))
    @Test fun keepsADomainInTheMiddleOfATitle() = assertEquals("Dot.com Boom and Bust", Titles.clean("Dot.com Boom and Bust"))
    @Test fun neverReturnsBlank() = assertEquals("(2019)", Titles.clean("(2019)"))
}
