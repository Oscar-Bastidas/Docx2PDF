package docx2pdf;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.ILeafElement;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import docxjavamapper.DocxJM;
import docxjavamapper.model.DJMDocument;
import docxjavamapper.model.DJMHyperlink;
import docxjavamapper.model.DJMParagraph;
import docxjavamapper.model.DJMRun;
import docxjavamapper.model.DJMTable;
import docxjavamapper.model.drawing.DJMAnchor;
import docxjavamapper.model.table.DJMTableCell;
import docxjavamapper.model.table.DJMTableRow;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public class Converter {

    private static final Logger LOGGER = Logger.getLogger(Converter.class.getName());
    private final File docx;
    private final String fontsFolder;
    
    // Keep a running index counter at the class level or pass it along during conversion
    private int imageSequenceIndex = 0;

    private java.io.File mediaTempFolder;

    DocxJM mapper = new DocxJM();

    /**
     * Starts the converting process with a specified folder with custom fonts
     *
     * @param in
     * @param out
     * @param fontsFolder path to the folder with custom fonts
     * @throws java.io.FileNotFoundException if the Docx file doesn't exist
     */
        public Converter(String in, String out, String fontsFolder) throws FileNotFoundException {
        docx = new File(in);
        /* Checks whether the provided Docx file exists */
        if (!docx.exists() || docx.isDirectory()) {
            throw new FileNotFoundException("The Docx document doesn't exist: " + in);
        }

        /* Unpack all media items from the docx archive to a temp directory on disk for robust loading */
        this.mediaTempFolder = Helper.extractAllMediaToTempFolder(docx);

        /* Checks if a folder for custom fonts has been provided */
        if (fontsFolder != null && !fontsFolder.trim().isEmpty()) {
            this.fontsFolder = fontsFolder;
        } else {
            this.fontsFolder = "";
        }

        try {
            InputStream str = Helper.getDocument(docx);
            DJMDocument djmDoc = mapper.map(str);

            convert(djmDoc, out);
        } catch (IOException ex) {
            Logger.getLogger(Converter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

       public void convert(DJMDocument djmDoc, String docOut) throws IOException {
        // Reset image sequence index at the start of every conversion run
        this.imageSequenceIndex = 0;

        // Dynamically extract page size and margins from the Word file
        com.itextpdf.kernel.geom.PageSize pageSize = Helper.getPageSize(docx);
        float[] docMargins = Helper.getMargins(docx);

        try (PdfWriter writer = new PdfWriter(docOut);
		// ORIGINAL SINGLE LINE BELOW:
             //PdfDocument pdfDocument = new PdfDocument(writer, new com.itextpdf.kernel.pdf.WriterProperties());
		PdfDocument pdfDocument = new PdfDocument(writer);

             Document pdfDoc = new Document(pdfDocument, pageSize)) {

            pdfDoc.setMargins(docMargins[0], docMargins[1], docMargins[2], docMargins[3]);

            // Load paragraph indentation map by sequence index
            java.util.Map<Integer, float[]> indentsMap = Helper.getTopLevelParagraphIndents(docx);

            final int[] paragraphIndex = {0};
            djmDoc.getBody().getBodyElements().forEach(be -> {
                if (be instanceof DJMParagraph) {
                    DJMParagraph djmp = (DJMParagraph) be;
                    float[] indents = indentsMap.get(paragraphIndex[0]);
                    pdfDoc.add(processParagraph(djmp, indents));
                    paragraphIndex[0]++;
                } else if (be instanceof DJMTable) {
                    DJMTable djmt = (DJMTable) be;
                    pdfDoc.add(processTable(djmt));
                    paragraphIndex[0]++;
                }
            });
        }
    }

   
    /**
     * Processes a paragraph.
     *
     * @param djmp the paragraph element
     * @return the processed paragraph
     */
        // TEMPORARY FLAG 3
    // 1-parameter overload for tables or anywhere else calling without indents
    private Paragraph processParagraph(DJMParagraph djmp) {
        return processParagraph(djmp, null);
    }

        // 2-parameter method delegates directly to the 3-parameter master method
    private Paragraph processParagraph(DJMParagraph djmp, float[] indents) {
        return processParagraph(djmp, indents, null);
    }
	

    // 3-parameter master method containing indents, line spacing, and run processing
    	private Paragraph processParagraph(DJMParagraph djmp, float[] indents, Float lineSpacingMultiplier) {
        Paragraph paragraph = new Paragraph();

        if (indents != null) {
            if (indents[0] != 0f) {
                paragraph.setFirstLineIndent(indents[0]);
            }
            if (indents[1] != 0f) {
                paragraph.setMarginLeft(indents[1]);
            }
        }

        // Handle line spacing
        if (lineSpacingMultiplier == null) {
            paragraph.setMultipliedLeading(1.0f); // Preserving your tuned setting
        } else {
            paragraph.setMultipliedLeading(lineSpacingMultiplier);
        }

        if (djmp.getParagraphElements() == null) {
            return paragraph;
        }

        List<?> elements = djmp.getParagraphElements();
        Text previousText = null;
        int i = 0;

        while (i < elements.size()) {
            Object pe = elements.get(i);

            if (pe instanceof DJMRun) {
                DJMRun currentRun = (DJMRun) pe;

                // Merge consecutive DJMRun elements that share identical formatting
                if (!hasPicture(currentRun) && currentRun.getText() != null) {
                    StringBuilder mergedText = new StringBuilder(currentRun.getText());
                    int nextIdx = i + 1;

                    while (nextIdx < elements.size() && elements.get(nextIdx) instanceof DJMRun) {
                        DJMRun nextRun = (DJMRun) elements.get(nextIdx);
                        if (!hasPicture(nextRun) && nextRun.getText() != null && haveSameFormatting(currentRun, nextRun)) {
                            mergedText.append(nextRun.getText());
                            nextIdx++;
                        } else {
                            break;
                        }
                    }

                    // Normalize smart punctuation to match formatText behavior
                    String finalStr = mergedText.toString()
                            .replace("\u2019", "'")
                            .replace("\u2018", "'")
                            .replace("\u201C", "\"")
                            .replace("\u201D", "\"");

                    for (ILeafElement le : processRun(currentRun)) {
                        if (le instanceof Text) {
                            Text t = (Text) le;
                            t.setText(finalStr);
                            t.setCharacterSpacing(0f);

                            // Close the sub-pixel boundary gap iText introduces between adjacent
                            // Text elements of differing font sizes when no space exists between them
                            if (previousText != null
                                    && !previousText.getText().endsWith(" ")
                                    && !finalStr.startsWith(" ")) {
                                com.itextpdf.layout.property.UnitValue prevSize =
                                        previousText.getProperty(com.itextpdf.layout.property.Property.FONT_SIZE);
                                com.itextpdf.layout.property.UnitValue currSize =
                                        t.getProperty(com.itextpdf.layout.property.Property.FONT_SIZE);
                                			
				// BELOW IS NEWEST VERSION FOR TRYING TO FIX SPACING BETWEEN CHARACTERS OF DIFFERING FONT SIZE IN TABLE CELLS:
			if (prevSize != null && currSize != null && prevSize.getValue() != currSize.getValue()) {
    String prevStr = previousText.getText();
    if (prevStr.length() == 1) {
        previousText.setCharacterSpacing(-1.50f);
    } else {
        previousText.setText(prevStr.substring(0, prevStr.length() - 1));
        Text lastCharText = new Text(prevStr.substring(prevStr.length() - 1));
        com.itextpdf.kernel.font.PdfFont prevFont =
                previousText.getProperty(com.itextpdf.layout.property.Property.FONT);
        if (prevFont != null) {
            lastCharText.setFont(prevFont);
        }
        lastCharText.setFontSize(prevSize.getValue());

	// Supposedly here's where I'd add the formattig stuff:
	if (previousText.hasProperty(com.itextpdf.layout.property.Property.FONT_WEIGHT)) {
            lastCharText.setBold();
        }
        if (previousText.hasProperty(com.itextpdf.layout.property.Property.FONT_STYLE)) {
            lastCharText.setItalic();
        }
        if (previousText.hasProperty(com.itextpdf.layout.property.Property.UNDERLINE)) {
            lastCharText.setUnderline();
        }
	if (previousText.hasProperty(com.itextpdf.layout.property.Property.FONT_COLOR)) {
            com.itextpdf.layout.property.TransparentColor tc =
                    previousText.getProperty(com.itextpdf.layout.property.Property.FONT_COLOR);
            if (tc != null) {
                lastCharText.setFontColor(tc.getColor());
            }
        }

        lastCharText.setCharacterSpacing(-1.50f);
        paragraph.add(lastCharText);
    }
}
                            }

                            previousText = t;
                        }
                        paragraph.add(le);
                    }
                    i = nextIdx;
                } else {
                    processRun(currentRun).forEach(paragraph::add);
                    previousText = null;
                    i++;
                }

            } else if (pe instanceof DJMHyperlink) {
                DJMHyperlink djmh = (DJMHyperlink) pe;
                paragraph.add(getTarget(djmh));
                previousText = null;
                i++;
            } else {
                i++;
            }
        }

        return paragraph;
    }

    private boolean hasPicture(DJMRun run) {
        try {
            return extractPictures(run) != null;
        } catch (IOException e) {
            return false;
        }
    }

       
    private boolean haveSameFormatting(DJMRun r1, DJMRun r2) {
        docxjavamapper.model.DJMRunProperties p1 = r1.getRunProperties();
        docxjavamapper.model.DJMRunProperties p2 = r2.getRunProperties();
        if (p1 == null && p2 == null) return true;
        if (p1 == null || p2 == null) return false;

        Float sz1 = (p1.getFontSize() != null) ? p1.getFontSize().getValue() : null;
        Float sz2 = (p2.getFontSize() != null) ? p2.getFontSize().getValue() : null;

        return java.util.Objects.equals(p1.isBold(), p2.isBold()) &&
               java.util.Objects.equals(p1.isItalic(), p2.isItalic()) &&
               java.util.Objects.equals(p1.isUnderline(), p2.isUnderline()) &&
               java.util.Objects.equals(p1.isStrike(), p2.isStrike()) &&
               java.util.Objects.equals(sz1, sz2) &&
               java.util.Objects.equals(
                   p1.getFont() != null ? p1.getFont().getValue() : null,
                   p2.getFont() != null ? p2.getFont().getValue() : null) &&
               java.util.Objects.equals(
                   p1.getColor() != null ? p1.getColor().getValue() : null,
                   p2.getColor() != null ? p2.getColor().getValue() : null) &&
               java.util.Objects.equals(
                   p1.getHighlight() != null ? p1.getHighlight().getValue() : null,
                   p2.getHighlight() != null ? p2.getHighlight().getValue() : null);
    }



    private List<ILeafElement> processRun(DJMRun djmr) {
        List<ILeafElement> leafElements = new LinkedList<>();

        Text text;

        // Text formatting
        text = formatText(djmr);
        text = setFontSize(djmr, text);
        text = setFontFamily(djmr, text);
        text = colorText(djmr, text);
        text = highlightText(djmr, text);

        // Adding images
        try {
            Image image = extractPictures(djmr);
            if (image != null) {
                leafElements.add(image);
            }
        } catch (IOException ex) {
            // TODO: If image couldn't be processed, show error image instead, 
            text = new Text("[Image could not be processed]");
            LOGGER.log(Level.SEVERE, "Error while processing image", ex);
        }

        leafElements.add(text);
        return leafElements;
    }

    /**
     * Processes a table.
     *
     * @param djmt the Table element
     * @return processed table
     */
        private Table processTable(DJMTable djmt) {
        int numCells = djmt.getTableRows().get(0).getTableCell().size();

        Table table = new Table(com.itextpdf.layout.property.UnitValue.createPercentArray(numCells))
                .useAllAvailableWidth();
        Cell cell;

        for (DJMTableRow r : djmt.getTableRows()) {
            for (DJMTableCell c : r.getTableCell()) {
                cell = new Cell();
                for (DJMParagraph p : c.getParagraph()) {
                    Paragraph para = processParagraph(p);
                    para.setTextAlignment(com.itextpdf.layout.property.TextAlignment.LEFT);
                    cell.add(para);
                }
                table.addCell(cell);
            }
        }

        return table;
    }

    /**
     * Applies possible text formatting.
     *
     * @param djmr corresponding Run
     * @return formatted text
     */
    	private Text formatText(DJMRun djmr) {
        if (djmr.getText() == null) {
            return new Text("");
        }

        String rawText = djmr.getText()
                .replace("\u2019", "'")   // Right single curly quote (Word's default apostrophe)
                .replace("\u2018", "'")   // Left single curly quote
                .replace("\u201C", "\"")  // Left double curly quote
                .replace("\u201D", "\""); // Right double curly quote

        Text text = new Text(rawText);

        if (djmr.getRunProperties() == null) {
            return text;
        }

        /* Bold */
        if (Boolean.TRUE.equals(djmr.getRunProperties().isBold())) {
            text.setBold();
        }
        /* Italic */
        if (Boolean.TRUE.equals(djmr.getRunProperties().isItalic())) {
            text.setItalic();
        }
        /* Underline */
        if (Boolean.TRUE.equals(djmr.getRunProperties().isUnderline())) {
            text.setUnderline();
        }
        /* Strike-through */
        if (Boolean.TRUE.equals(djmr.getRunProperties().isStrike())) {
            text.setLineThrough();
        }

        return text;
    }


    /**
     * Sets the font size. In OOXML the font size is specified in half-points.
     * iText needs the size in points, so it should be divided by 2.
     *
     * @param djmr
     * @param text
     * @return
     */
    	private Text setFontSize(DJMRun djmr, Text text) {
        if (djmr.getText() == null || djmr.getRunProperties() == null) {
            return text;
        }

        // If the font size is null, it inherits Word's default body style (11pt)
        if (djmr.getRunProperties().getFontSize() == null) {
            text.setFontSize(11.0f); // Apply calibration to un-tagged body text
            return text;
        }

        try {
            float rawSize = djmr.getRunProperties().getFontSize().getValue() / 2f;
            
            // Apply calibration to any explicit 11pt runs as well
            if (rawSize == 11.0f) {
                text.setFontSize(10.25f); // Fine-tune this float if needed
            } else {
                text.setFontSize(rawSize);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error setting font size", ex);
        }

        return text;
    }

    /**
     * Sets the font family. The font file should be provided in a TTF format in
     * a font folder. The font name is case sensitive, i.e. it should have
     * exactly the same name like in the DOCX file. Otherwise a standard font
     * (Helvetica) will be used.
     *
     * @param djmr
     * @param text
     * @return
     */
    
    private Text setFontFamily(DJMRun djmr, Text text) {
        if (djmr.getText() == null || djmr.getRunProperties() == null || djmr.getRunProperties().getFont() == null || djmr.getRunProperties().getFont().getValue() == null) {
            return text;
        }

        if (!fontsFolder.isEmpty()) {
            PdfFont font;
            String fontValue = djmr.getRunProperties().getFont().getValue();
            FontProgram fontProgram;

            try {
                fontProgram = Helper.loadFont(fontValue, fontsFolder);
		// Original "font = PdfFontFactory.createFont(...)" is immediately below - encoding here MAY be preventing
		// apostrophes from showing up
                //font = PdfFontFactory.createFont(fontProgram, PdfEncodings.UTF8, true);
		// Newest "font = PdfFontFactory.createFont(...)" is immediately below - tries to correct disappearing
		// apostrophes
		font = PdfFontFactory.createFont(fontProgram, com.itextpdf.io.font.PdfEncodings.IDENTITY_H, true);
                text.setFont(font);
            } catch (NullPointerException ex) {
                LOGGER.log(Level.WARNING, "Error while reading the font", ex);
            }
        }

        return text;
    }

    /**
     * Returns possible text color.
     *
     * @param djmr corresponding Run. The possible color information is stored
     * here.
     * @param text Text to be colored
     * @return colored text
     */
    
    private Text colorText(DJMRun djmr, Text text) {
        if (djmr.getText() == null || djmr.getRunProperties() == null || djmr.getRunProperties().getColor() == null) {
            return text;
        }

        try {
            String value = djmr.getRunProperties().getColor().getValue();
            Color color = Helper.convertColor(value);
            text.setFontColor(color);
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, ex.toString());
        }
        return text;
    }


    /**
     * Highlights the text.
     *
     * @param djmr corresponding Run. The possible color information is stored
     * @param text Text to be highlighted
     * @return highlighted text
     */
       private Text highlightText(DJMRun djmr, Text text) {
        if (djmr.getText() == null || djmr.getRunProperties() == null || djmr.getRunProperties().getHighlight() == null) {
            return text;
        }

        try {
            String value = djmr.getRunProperties().getHighlight().getValue();
            Color color = Helper.convertColor(value);
            text.setBackgroundColor(color);
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, ex.toString());
        }
        return text;
    }

    /**
     * Extracts possible images. The image needs to be anchored to a paragraph,
     * in order to be positioned correctly.
     *
     * @param run
     * @return Extracted image
     * @throws IOException
     */
    	private Image extractPictures(DJMRun run) throws IOException {
        if (run.getDrawing() == null) {
            return null;
        }

        DJMAnchor anchor = null;
        try {
            anchor = run.getDrawing().getAnchor();
        } catch (Exception ignored) {}

        float cx = 0;
        float cy = 0;
        int posH = 0;
        int posV = 0;

        // Try extracting dimensions from anchor if available
        if (anchor != null) {
            if (anchor.getExtent() != null) {
                cx = anchor.getExtent().getCx();
                cy = anchor.getExtent().getCy();
            }
            if (anchor.getPositionH() != null) {
                posH = anchor.getPositionH().getPosOffset();
            }
            if (anchor.getPositionV() != null) {
                posV = anchor.getPositionV().getPosOffset();
            }
        } else {
            // Fallback: Try inspecting the drawing object directly for extent if it's inline
            try {
                java.lang.reflect.Method getExtent = run.getDrawing().getClass().getMethod("getExtent");
                Object extent = getExtent.invoke(run.getDrawing());
                if (extent != null) {
                    java.lang.reflect.Method getCx = extent.getClass().getMethod("getCx");
                    java.lang.reflect.Method getCy = extent.getClass().getMethod("getCy");
                    Object cxObj = getCx.invoke(extent);
                    Object cyObj = getCy.invoke(extent);
                    if (cxObj instanceof Number) cx = ((Number) cxObj).floatValue();
                    if (cyObj instanceof Number) cy = ((Number) cyObj).floatValue();
                }
            } catch (Exception ignored) {}
        }

        // Retrieve sorted media files from the temp folder
        java.io.File[] sortedFiles = Helper.getSortedMediaFiles(mediaTempFolder);
        if (sortedFiles.length == 0) {
            return null;
        }

        // Use sequential document appearance order for all drawings
        java.io.File imageFile = null;
        if (imageSequenceIndex < sortedFiles.length) {
            imageFile = sortedFiles[imageSequenceIndex];
        } else {
            imageFile = sortedFiles[sortedFiles.length - 1];
        }

        imageSequenceIndex++;

        if (imageFile == null || !imageFile.exists()) {
            return null;
        }

        // Load image directly from disk using iText's ImageDataFactory
        ImageData id = ImageDataFactory.create(imageFile.getAbsolutePath());
        Image image = new Image(id);

        if (cx > 0) {
            // Updated true typographic point conversion factor (1 inch = 72 points = 914,400 EMUs -> 12,700 EMUs per point)
            final int EMU = 12700;
            cx = cx / EMU;
            cy = cy / EMU;
            posH = posH / EMU;
            posV = posV / EMU;

            image.setWidth(cx);
            if (cy > 0) image.setHeight(cy);
            if (posV > 0) image.setMarginTop(posV);
            if (posH > 0) image.setMarginLeft(posH);
        } else {
            // Fallback for images lacking explicit EMU extents
            image.scaleToFit(400, 400);
        }

        return image;
    }



    /**
     * Processes a hyperlink.
     *
     * @param djmh
     * @return iText Link
     */
    	private Link getTarget(DJMHyperlink djmh) {
        String id = djmh.getId();
        DJMRun djmr = djmh.getRun();
        Link link = null;
        try {
            String target = Helper.getHyperlink(docx, id);
            link = new Link(djmh.getRun().getText(), PdfAction.createURI(target));

            // Remove the default PDF link annotation border box
            link.getLinkAnnotation().setBorder(new com.itextpdf.kernel.pdf.PdfArray(new float[]{0, 0, 0}));

            formatText(djmr);
            setFontSize(djmr, link);
            setFontFamily(djmr, link);
            colorText(djmr, link);
            highlightText(djmr, link);
        } catch (IOException ex) {
            Logger.getLogger(Converter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return link;
    } // END OF GETTARGET METHOD
}
