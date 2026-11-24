package docx2pdf;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        String docIn = "/data/data/com.termux/files/home/storage/shared/Download/Docx2PDF/target/document.docx";
        String docOut = "/data/data/com.termux/files/home/storage/shared/Download/Docx2PDF/target/test_out.pdf";
        String fontsPath = "/data/data/com.termux/files/home/storage/shared/Download/Docx2PDF/target/my-fonts/";

        try {
		// DIAGNOSTIC: INTERCEPTS WORD'S XML INCARNATION TO SEE WHY INDENTS AREN'T RENDERING
		Helper.debugParagraphIndentation(new java.io.File(docIn));

            Converter app = new Converter(docIn, docOut, fontsPath);

            // fontPath can be null. In this case, a standard font will be used: Helvetica
            // Converter app = new Converter(docIn, docOut, null);
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "File not found", ex);
        }
    }

}
