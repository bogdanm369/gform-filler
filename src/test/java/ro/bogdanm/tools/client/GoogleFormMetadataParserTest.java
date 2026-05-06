package ro.bogdanm.tools.client;

import org.junit.jupiter.api.Test;
import ro.bogdanm.tools.model.GoogleFormMetadata;
import ro.bogdanm.tools.model.QuestionType;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleFormMetadataParserTest {

    private final GoogleFormMetadataParser parser = new GoogleFormMetadataParser();

    @Test
    void parsesEmbeddedLoadDataAndSkipsSectionHeaders() throws Exception {
        String html = """
                <html>
                  <body>
                    <form id="mG61Hd" action="https://docs.google.com/forms/d/e/test/formResponse">
                      <input type="hidden" name="fvv" value="1" />
                      <input type="hidden" name="fbzx" value="abc123" />
                      <input type="hidden" name="pageHistory" value="0" />
                      <input type="hidden" name="partialResponse" value='[null,null,"abc123"]' />
                      <input type="hidden" name="submissionTimestamp" value="-1" />
                    </form>
                    <div role="heading" aria-level="1">My Survey</div>
                    <div class="cBGGJ">Survey description</div>
                    <script>
                      var FB_PUBLIC_LOAD_DATA_ = [null,["desc",[
                        [1001,"Consent",null,4,[[2001,[["Yes",null,null,null,0]],1,null,null,null,null,null,0]]],
                        [1002,"Section",null,8,null],
                        [1003,"Department",null,0,[[2003,null,1]]],
                        [1004,"Scale",null,5,[[2004,[["1"],["2"],["3"]],1,["Low","High"]]]]
                      ],null,null,null,null,null,null,"My Survey"]];
                    </script>
                  </body>
                </html>
                """;

        GoogleFormMetadata metadata = parser.parse(URI.create("https://docs.google.com/forms/d/e/test/viewform"), html);

        assertEquals("My Survey", metadata.title());
        assertEquals("abc123", metadata.fbzx());
        assertEquals(3, metadata.questions().size());
        assertEquals("Consent", metadata.questions().get(0).title());
        assertEquals(QuestionType.CHECKBOX, metadata.questions().get(0).type());
        assertEquals("Department", metadata.questions().get(1).title());
        assertEquals(QuestionType.SHORT_TEXT, metadata.questions().get(1).type());
        assertEquals("Scale", metadata.questions().get(2).title());
        assertEquals(QuestionType.LINEAR_SCALE, metadata.questions().get(2).type());
        assertEquals(3, metadata.questions().get(2).options().size());
        assertEquals(2, metadata.pages().size());
        assertEquals(List.of("Consent"), metadata.pages().get(0).questions().stream().map(question -> question.title()).toList());
        assertEquals(List.of("Department", "Scale"), metadata.pages().get(1).questions().stream().map(question -> question.title()).toList());
        assertTrue(metadata.questions().get(0).required());
        assertTrue(metadata.questions().get(1).options().isEmpty());
    }
}

