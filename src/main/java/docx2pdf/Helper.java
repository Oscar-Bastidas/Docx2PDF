package docx2pdf;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.constants.StandardFontFamilies;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import docxjavamapper.model.relationships.DJMRelationships;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class Helper {

    private Helper() {
    }

    private static final Logger LOGGER = LogManager.getLogger(Helper.class);

    /**
     * Returns the stream to a document.xml from the DOCX-archive.
     *
     * @param docx the Docx file
     * @return the InputStream of the document.xml
     * @throws IOException
     */
    public static InputStream getDocument(File docx) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        return zipFile.getInputStream(zipFile.getEntry("word/document.xml"));
    }

    /**
     * Parses document.xml_rels file inside of the word/_rels directory of the
     * Docx archive. The relationships between some ids (key) and their values
     * are stored there. In this case the target URL of the hyperlink will be
     * found by id.
     *
     * @param docx
     * @param id
     * @return
     * @throws IOException
     */
    public static String getHyperlink(File docx, String id) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        InputStream is = zipFile.getInputStream(zipFile.getEntry("word/_rels/document.xml.rels"));

        DJMRelationships djmRels = null;

        JAXBContext jaxbContext;
        String target = null;
        try {
            jaxbContext = JAXBContext.newInstance(DJMRelationships.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            djmRels = (DJMRelationships) jaxbUnmarshaller.unmarshal(is);

            target = djmRels.getRelationships().stream().filter(x -> x.getId().equals(id)).findFirst().get().getTarget();
        } catch (JAXBException ex) {
            LOGGER.error(ex);
        }

        return target;
    }

    /**
     * Returns a stream to an image from the DOCX-archive.
     *
     * @param docx the Docx file
     * @param name name of the desired image
     * @return the InputStream of the document.xml
     * @throws IOException
     */
    // BELOW IS ORIGINAL: BELOW CODE CAN NOT HANDLE JPEG IMAGES
    /* public static InputStream getImage(File docx, String name) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        // TODO: Load images dynamically (extension)
        return zipFile.getInputStream(zipFile.getEntry("word/media/" + name.toLowerCase() + ".png"));
    } */
    // The below version had the nasty "var" variable type which my compiler cannot resolve since apparently I'm compiling with a lower compiler despite using java 17
    /* public static InputStream getImage(File docx, String name) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        InputStream is = null;

        // Word image references might include extensions or omit them
        String[] extensions = {"", ".png", ".jpg", ".jpeg", ".JPG", ".JPEG", ".PNG"};

        for (String ext : extensions) {
            var entry = zipFile.getEntry("word/media/" + name + ext);
            if (entry != null) {
                is = zipFile.getInputStream(entry);
                break;
            }
        }
        return is;
    } */
    public static InputStream getImage(File docx, String name) throws IOException {
        ZipFile zipFile = new ZipFile(docx);
        InputStream is = null;

        // Word image references might include extensions or omit them
        String[] extensions = {"", ".png", ".jpg", ".jpeg", ".JPG", ".JPEG", ".PNG"};

        for (String ext : extensions) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/media/" + name + ext);
            if (entry != null) {
                is = zipFile.getInputStream(entry);
                break;
            }
        }
        return is;
    }

    /**
     * Loads the required font as TTF in the specified folder. If the required
     * font can't be find, a standard font will be used.
     *
     * @param fontValue name of the required font (case sensitive)
     * @param fontsFolder path of the folder with the required font(s)
     * @return FontProgram, an iText class for specifying required fonts
     */
    public static FontProgram loadFont(String fontValue, String fontsFolder) {
        FontProgram fontProgram = null;
        String fontPath = fontsFolder.concat(fontValue).concat(".ttf");
        try {
            fontProgram = FontProgramFactory.createFont(fontPath);
        } catch (IOException ex) {
            LOGGER.warn("Font {0} could not be found in {}.\n{}", fontValue, fontsFolder, ex);
            try {
                fontProgram = FontProgramFactory.createFont(StandardFontFamilies.HELVETICA);
            } catch (IOException ex2) {
                LOGGER.warn("Neither the required Font {}, nor the standard font Helvetica could be loaded.\n{}", fontValue, fontsFolder, ex);

            }
        }

        return fontProgram;
    }

    /**
     * Converts a color from String to RGB. A color can be in HEX format or as a
     * word (constant). First it will be checked, if that constant is
     * recognizable by iText. If that's not the case, it should be HEX format,
     * so it will be parsed.
     *
     * @param hex
     * @return
     * @throws Exception if an unexpected exception occurs
     */
    public static Color convertColor(String hex) {
        java.awt.Color colorAwt = null;
        Color color = null;

        /* Color "auto" will be treated as black */
        if (hex.equals("auto")) {
            color = new DeviceRgb(java.awt.Color.black);
        } else {
            /* Checks whether that color exists as constant in iText class */
            try {
                Field f = java.awt.Color.class.getField(hex);
                colorAwt = (java.awt.Color) f.get(null);
                color = new DeviceRgb(colorAwt);
            } catch (NoSuchFieldException e) {
                validateHexColor(hex);

                /* If the color constant doesn't exist, this part will be executed */
                int r = Integer.valueOf(hex.substring(0, 2), 16);
                int g = Integer.valueOf(hex.substring(2, 4), 16);
                int b = Integer.valueOf(hex.substring(4, 6), 16);
                color = new DeviceRgb(r, g, b);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOGGER.error(ex);
            }
        }

        return color;
    }

    /**
     * Validates a given color. Valid color formats are: #000080, #fff, #FFFFFF,
     * red etc.
     *
     * @param color the color to validate
     * @return result
     */
    private static boolean validateHexColor(String color) {
        if (color != null) {
            Pattern pattern = Pattern.compile("^#?(?:[0-9a-fA-F]{3}){1,2}$");
            Matcher matcher = pattern.matcher(color);

            if (matcher.matches()) {
                return true;
            } else {
                throw new NumberFormatException(color + " could not be recognized as a valid color");
            }
        }
        return false;
    }

	// A NEWLY ADDED METHOD TO DYNAMICALLY READ AND PRESERVE THE MARGINS FROM THE ORIGINAL WORD FILE
    	/**
     * Reads page margins from word/document.xml and returns them in points [top, right, bottom, left].
     * Word uses twips (1 pt = 20 twips). Defaults to 72 points (1 inch) if not found.
     */
    // THE FRESHLY COMPOSED/INTRODUCED METHOD BELOW USES "var" which is a Java 10 hing - and my pom isn't compiling
    // for Java 10 but rather Java 8 I believe
    /*public static float[] getMargins(File docx) {
        float[] margins = {72f, 72f, 72f, 72f}; // Default 1 inch
        try (ZipFile zipFile = new ZipFile(docx)) {
            var entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return margins;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.NodeList nodeList = doc.getElementsByTagName("w:pgMar");
            if (nodeList.getLength() > 0) {
                org.w3c.dom.Element pgMar = (org.w3c.dom.Element) nodeList.item(nodeList.getLength() - 1); // Get last sectPr margins
                
                float top = Float.parseFloat(pgMar.getAttribute("w:top")) / 20f;
                float right = Float.parseFloat(pgMar.getAttribute("w:right")) / 20f;
                float bottom = Float.parseFloat(pgMar.getAttribute("w:bottom")) / 20f;
                float left = Float.parseFloat(pgMar.getAttribute("w:left")) / 20f;

                margins = new float[]{top, right, bottom, left};
            }
        } catch (Exception e) {
            // Fallback to defaults on any parse error
        }
        return margins;
    } */
    /**
     * Reads page margins from word/document.xml and returns them in points [top, right, bottom, left].
     * Word uses twips (1 pt = 20 twips). Defaults to 72 points (1 inch) if not found.
     */
    public static float[] getMargins(File docx) {
        float[] margins = {72f, 72f, 72f, 72f}; // Default 1 inch
        try (ZipFile zipFile = new ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return margins;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.NodeList nodeList = doc.getElementsByTagName("w:pgMar");
            if (nodeList.getLength() > 0) {
                org.w3c.dom.Element pgMar = (org.w3c.dom.Element) nodeList.item(nodeList.getLength() - 1); // Get last sectPr margins

                float top = Float.parseFloat(pgMar.getAttribute("w:top")) / 20f;
                float right = Float.parseFloat(pgMar.getAttribute("w:right")) / 20f;
                float bottom = Float.parseFloat(pgMar.getAttribute("w:bottom")) / 20f;
                float left = Float.parseFloat(pgMar.getAttribute("w:left")) / 20f;

                margins = new float[]{top, right, bottom, left};
            }
        } catch (Exception e) {
            // Fallback to defaults on any parse error
        }
        return margins;
    }

    // DIAGNOSTIC: INTERCEPTS WORD'S XML INCARNATION TO SEE WHY FINAL PDF PRODUCT ISN'T RENDERING INDENTS
    /**
     * Diagnostic interceptor to inspect raw XML indentation in word/document.xml
     */
    public static void debugParagraphIndentation(File docx) {
        try (ZipFile zipFile = new ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.NodeList paragraphs = doc.getElementsByTagName("w:p");
            System.out.println("--- RAW XML INDENTATION DIAGNOSTIC START ---");
            for (int i = 0; i < paragraphs.getLength(); i++) {
                org.w3c.dom.Element p = (org.w3c.dom.Element) paragraphs.item(i);
                org.w3c.dom.NodeList pPrs = p.getElementsByTagName("w:pPr");
                if (pPrs.getLength() > 0) {
                    org.w3c.dom.Element pPr = (org.w3c.dom.Element) pPrs.item(0);
                    org.w3c.dom.NodeList inds = pPr.getElementsByTagName("w:ind");
                    if (inds.getLength() > 0) {
                        org.w3c.dom.Element ind = (org.w3c.dom.Element) inds.item(0);
                        String left = ind.getAttribute("w:left");
                        String firstLine = ind.getAttribute("w:firstLine");
                        String hanging = ind.getAttribute("w:hanging");

                        // Grab some text from the paragraph for context
                        org.w3c.dom.NodeList texts = p.getElementsByTagName("w:t");
                        String sampleText = (texts.getLength() > 0) ? texts.item(0).getTextContent() : "[No text]";
                        if (sampleText.length() > 20) sampleText = sampleText.substring(0, 20) + "...";

                        System.out.println(String.format("Paragraph [%d] ('%s') -> w:left='%s', w:firstLine='%s', w:hanging='%s'",
                            i, sampleText, left, firstLine, hanging));
                    }
                }
            }
            System.out.println("--- RAW XML INDENTATION DIAGNOSTIC END ---");
        } catch (Exception e) {
            System.err.println("Error running indentation diagnostic: " + e.getMessage());
        }
    }

    // **
    // * Extracts top-level paragraph indentation map (index -> [firstLinePt, leftPt]) from word/document.xml
    // */
    /* public static java.util.Map<Integer, float[]> getTopLevelParagraphIndents(File docx) {
        java.util.Map<Integer, float[]> indentMap = new java.util.HashMap<>();
        try (ZipFile zipFile = new ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return indentMap;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.Node bodyNode = doc.getElementsByTagName("w:body").item(0);
            if (bodyNode == null) return indentMap;

            org.w3c.dom.NodeList bodyChildren = bodyNode.getChildNodes();
            int topLevelIndex = 0;
            for (int i = 0; i < bodyChildren.getLength(); i++) {
                org.w3c.dom.Node node = bodyChildren.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String nodeName = node.getNodeName();
                    if ("w:p".equals(nodeName)) {
                        org.w3c.dom.Element p = (org.w3c.dom.Element) node;
                        org.w3c.dom.NodeList pPrs = p.getElementsByTagName("w:pPr");
                        if (pPrs.getLength() > 0) {
                            org.w3c.dom.Element pPr = (org.w3c.dom.Element) pPrs.item(0);
                            org.w3c.dom.NodeList inds = pPr.getElementsByTagName("w:ind");
                            if (inds.getLength() > 0) {
                                org.w3c.dom.Element ind = (org.w3c.dom.Element) inds.item(0);

                                float firstLinePt = 0f;
                                float leftPt = 0f;

                                String firstLineStr = ind.getAttribute("w:firstLine");
                                if (firstLineStr != null && !firstLineStr.isEmpty()) {
                                    firstLinePt = Float.parseFloat(firstLineStr) / 20f;
                                } else {
                                    String hangingStr = ind.getAttribute("w:hanging");
                                    if (hangingStr != null && !hangingStr.isEmpty()) {
                                        firstLinePt = -(Float.parseFloat(hangingStr) / 20f);
                                    }
                                }

                                String leftStr = ind.getAttribute("w:left");
                                if (leftStr != null && !leftStr.isEmpty()) {
                                    leftPt = Float.parseFloat(leftStr) / 20f;
                                }

                                if (firstLinePt != 0f || leftPt != 0f) {
                                    indentMap.put(topLevelIndex, new float[]{firstLinePt, leftPt});
                                }
                            }
                        }
                        topLevelIndex++;
                    } else if ("w:tbl".equals(nodeName)) {
                        topLevelIndex++;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback silently
        }
        return indentMap;
    } */
    /* public static java.util.Map<Integer, float[]> getTopLevelParagraphIndents(File docx) {
        java.util.Map<Integer, float[]> indentMap = new java.util.HashMap<>();
        try (ZipFile zipFile = new ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return indentMap;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.Node bodyNode = doc.getElementsByTagName("w:body").item(0);
            if (bodyNode == null) return indentMap;

            org.w3c.dom.NodeList bodyChildren = bodyNode.getChildNodes();
            int topLevelIndex = 0;

            for (int i = 0; i < bodyChildren.getLength(); i++) {
                org.w3c.dom.Node node = bodyChildren.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String nodeName = node.getNodeName();

                    if ("w:p".equals(nodeName)) {
                        org.w3c.dom.Element p = (org.w3c.dom.Element) node;
                        float firstLinePt = 0f;
                        float leftPt = 0f;
                        boolean hasExplicitIndent = false;
                        String styleId = "";

                        org.w3c.dom.NodeList pPrs = p.getElementsByTagName("w:pPr");
                        if (pPrs.getLength() > 0) {
                            org.w3c.dom.Element pPr = (org.w3c.dom.Element) pPrs.item(0);

                            // Check for style ID
                            org.w3c.dom.NodeList styles = pPr.getElementsByTagName("w:pStyle");
                            if (styles.getLength() > 0) {
                                styleId = ((org.w3c.dom.Element) styles.item(0)).getAttribute("w:val");
                            }

                            // Check for explicit indents
                            org.w3c.dom.NodeList inds = pPr.getElementsByTagName("w:ind");
                            if (inds.getLength() > 0) {
                                org.w3c.dom.Element ind = (org.w3c.dom.Element) inds.item(0);

                                String firstLineStr = ind.getAttribute("w:firstLine");
                                if (firstLineStr != null && !firstLineStr.isEmpty()) {
                                    firstLinePt = Float.parseFloat(firstLineStr) / 20f;
                                    hasExplicitIndent = true;
                                } else {
                                    String hangingStr = ind.getAttribute("w:hanging");
                                    if (hangingStr != null && !hangingStr.isEmpty()) {
                                        firstLinePt = -(Float.parseFloat(hangingStr) / 20f);
                                        hasExplicitIndent = true;
                                    }
                                }

                                String leftStr = ind.getAttribute("w:left");
                                if (leftStr != null && !leftStr.isEmpty()) {
                                    leftPt = Float.parseFloat(leftStr) / 20f;
                                    hasExplicitIndent = true;
                                }
                            }
                        }

                        // Style-aware fallback if no explicit indent tag exists
                        if (!hasExplicitIndent) {
                            if (styleId.isEmpty() || "Normal".equalsIgnoreCase(styleId) || "BodyText".equalsIgnoreCase(styleId)) {
                                firstLinePt = 36f; // Standard body indent fallback (adjust points as needed)
                            } else {
                                firstLinePt = 0f;  // Headings and custom styles get zero indent
                            }
                        }

                        // Always put an entry so every paragraph index is accounted for
                        indentMap.put(topLevelIndex, new float[]{firstLinePt, leftPt});
                        topLevelIndex++;

                    } else if ("w:tbl".equals(nodeName)) {
                        topLevelIndex++;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback silently
        }
        return indentMap;
    } */
	/* public static java.util.Map<Integer, float[]> getTopLevelParagraphIndents(File docx) {
        java.util.Map<Integer, float[]> indentMap = new java.util.HashMap<>();
        try (ZipFile zipFile = new ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return indentMap;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.Node bodyNode = doc.getElementsByTagName("w:body").item(0);
            if (bodyNode == null) return indentMap;

            org.w3c.dom.NodeList bodyChildren = bodyNode.getChildNodes();
            int topLevelIndex = 0;

            for (int i = 0; i < bodyChildren.getLength(); i++) {
                org.w3c.dom.Node node = bodyChildren.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String nodeName = node.getNodeName();

                    if ("w:p".equals(nodeName)) {
                        org.w3c.dom.Element p = (org.w3c.dom.Element) node;
                        float firstLinePt = 0f;
                        float leftPt = 0f;
                        boolean hasExplicitIndent = false;
                        String styleId = "";

                        org.w3c.dom.NodeList pPrs = p.getElementsByTagName("w:pPr");
                        if (pPrs.getLength() > 0) {
                            org.w3c.dom.Element pPr = (org.w3c.dom.Element) pPrs.item(0);

                            // 1. Extract the style ID (e.g., Normal, Heading1, Title, Subtitle)
                            org.w3c.dom.NodeList styles = pPr.getElementsByTagName("w:pStyle");
                            if (styles.getLength() > 0) {
                                styleId = ((org.w3c.dom.Element) styles.item(0)).getAttribute("w:val");
                            }

                            // 2. Check for explicit <w:ind> formatting tags
                            org.w3c.dom.NodeList inds = pPr.getElementsByTagName("w:ind");
                            if (inds.getLength() > 0) {
                                org.w3c.dom.Element ind = (org.w3c.dom.Element) inds.item(0);

                                String firstLineStr = ind.getAttribute("w:firstLine");
                                if (firstLineStr != null && !firstLineStr.isEmpty()) {
                                    firstLinePt = Float.parseFloat(firstLineStr) / 20f;
                                    hasExplicitIndent = true;
                                } else {
                                    String hangingStr = ind.getAttribute("w:hanging");
                                    if (hangingStr != null && !hangingStr.isEmpty()) {
                                        firstLinePt = -(Float.parseFloat(hangingStr) / 20f);
                                        hasExplicitIndent = true;
                                    }
                                }

                                String leftStr = ind.getAttribute("w:left");
                                if (leftStr != null && !leftStr.isEmpty()) {
                                    leftPt = Float.parseFloat(leftStr) / 20f;
                                    hasExplicitIndent = true;
                                }
                            }
                        }

                        // 3. Decision Chain: "Innocent Until Proven Headed"
                        if (hasExplicitIndent) {
                            // Rule A: Explicit XML indents always win
                            indentMap.put(topLevelIndex, new float[]{firstLinePt, leftPt});
                        } else if (isHeadingOrNonBodyStyle(styleId)) {
                            // Rule B: Proven to be a Heading, Title, Caption, or List -> Force 0f
                            indentMap.put(topLevelIndex, new float[]{0f, 0f});
                        } else {
                            // Rule C: Default "Innocent" Body Paragraph -> Apply standard 36f indent
                            indentMap.put(topLevelIndex, new float[]{36f, 0f});
                        }

                        topLevelIndex++;

                    } else if ("w:tbl".equals(nodeName)) {
                        topLevelIndex++;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback silently
        }
        return indentMap;
    }

    //
    //  Helper method to identify non-body styles that should remain flush-left (0f).
	//
    private static boolean isHeadingOrNonBodyStyle(String styleId) {
        if (styleId == null || styleId.isEmpty()) {
            return false; // Empty styleId in OpenXML defaults to "Normal" body text
        }
        String lower = styleId.toLowerCase();
        return lower.contains("heading") ||
               lower.contains("title") ||
               lower.contains("subtitle") ||
               lower.contains("header") ||
               lower.contains("footer") ||
               lower.contains("caption") ||
               lower.contains("list");
    } */ // END OF METHODS TO DO PROPER INDENTATION
	public static java.util.Map<Integer, float[]> getTopLevelParagraphIndents(File docx) {
        java.util.Map<Integer, float[]> indentMap = new java.util.HashMap<>();
        try (ZipFile zipFile = new ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return indentMap;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.Node bodyNode = doc.getElementsByTagName("w:body").item(0);
            if (bodyNode == null) return indentMap;

            org.w3c.dom.NodeList bodyChildren = bodyNode.getChildNodes();
            int topLevelIndex = 0;

            for (int i = 0; i < bodyChildren.getLength(); i++) {
                org.w3c.dom.Node node = bodyChildren.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String nodeName = node.getNodeName();

                    if ("w:p".equals(nodeName)) {
                        org.w3c.dom.Element p = (org.w3c.dom.Element) node;
                        float firstLinePt = 0f;
                        float leftPt = 0f;
                        boolean hasExplicitIndent = false;

                        // 1. Check for explicit <w:ind> formatting tags
                        org.w3c.dom.NodeList pPrs = p.getElementsByTagName("w:pPr");
                        if (pPrs.getLength() > 0) {
                            org.w3c.dom.Element pPr = (org.w3c.dom.Element) pPrs.item(0);
                            org.w3c.dom.NodeList inds = pPr.getElementsByTagName("w:ind");
                            if (inds.getLength() > 0) {
                                org.w3c.dom.Element ind = (org.w3c.dom.Element) inds.item(0);

                                String firstLineStr = ind.getAttribute("w:firstLine");
                                if (firstLineStr != null && !firstLineStr.isEmpty()) {
                                    firstLinePt = Float.parseFloat(firstLineStr) / 20f;
                                    hasExplicitIndent = true;
                                } else {
                                    String hangingStr = ind.getAttribute("w:hanging");
                                    if (hangingStr != null && !hangingStr.isEmpty()) {
                                        firstLinePt = -(Float.parseFloat(hangingStr) / 20f);
                                        hasExplicitIndent = true;
                                    }
                                }

                                String leftStr = ind.getAttribute("w:left");
                                if (leftStr != null && !leftStr.isEmpty()) {
                                    leftPt = Float.parseFloat(leftStr) / 20f;
                                    hasExplicitIndent = true;
                                }
                            }
                        }

                        // 2. If no <w:ind>, check if the paragraph starts with a manual <w:tab/>
                        if (!hasExplicitIndent && startsWithTab(p)) {
                            firstLinePt = 36f; // Standard 0.5-inch tab stop
                            hasExplicitIndent = true;
                        }

                        // 3. Only indent if explicitly tagged or tabbed; otherwise keep flush-left (0f)
                        if (hasExplicitIndent) {
                            indentMap.put(topLevelIndex, new float[]{firstLinePt, leftPt});
                        } else {
                            indentMap.put(topLevelIndex, new float[]{0f, 0f});
                        }

                        topLevelIndex++;

                    } else if ("w:tbl".equals(nodeName)) {
                        topLevelIndex++;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback silently
        }
        return indentMap;
    }

    /**
     * Checks whether the first content element inside a paragraph's runs is a <w:tab/> tag.
     */
    private static boolean startsWithTab(org.w3c.dom.Element p) {
        org.w3c.dom.NodeList runs = p.getElementsByTagName("w:r");
        if (runs.getLength() == 0) return false;

        org.w3c.dom.Element firstRun = (org.w3c.dom.Element) runs.item(0);
        org.w3c.dom.NodeList runChildren = firstRun.getChildNodes();
        for (int j = 0; j < runChildren.getLength(); j++) {
            org.w3c.dom.Node child = runChildren.item(j);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                if ("w:rPr".equals(name)) {
                    continue; // Skip run formatting properties
                }
                return "w:tab".equals(name);
            }
        }
        return false;
    }


    // Some methid added anew here for saving picturesnin word file to a temporary folder
    /**
     * Unpacks all images from word/media/ in the DOCX archive into a temporary directory on disk
     * and returns the directory path, mapping original filenames/indices.
     */
    public static java.io.File extractAllMediaToTempFolder(File docx) {
        java.io.File tempDir = null;
        try {
            tempDir = java.nio.file.Files.createTempDirectory("docx_media_").toFile();
            tempDir.deleteOnExit();

            try (ZipFile zipFile = new ZipFile(docx)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("word/media/")) {
                        String fileName = name.substring("word/media/".length());
                        java.io.File destFile = new java.io.File(tempDir, fileName);
                        try (InputStream in = zipFile.getInputStream(entry);
                             java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return tempDir;
    }

    /**
     * Returns all extracted media files sorted alphabetically by filename.
     */
    public static java.io.File[] getSortedMediaFiles(java.io.File tempDir) {
        if (tempDir == null || !tempDir.exists()) return new java.io.File[0];
        java.io.File[] files = tempDir.listFiles();
        if (files == null) return new java.io.File[0];

        // Sort files alphabetically to guarantee image1, image2, image3 sequence
        java.util.Arrays.sort(files, new java.util.Comparator<java.io.File>() {
            @Override
            public int compare(java.io.File f1, java.io.File f2) {
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        });
        return files;
    }
	
    /**
 * Reads page size from word/document.xml and returns a PageSize object.
 * Defaults to Letter if not found.
 */
public static com.itextpdf.kernel.geom.PageSize getPageSize(File docx) {
    try (ZipFile zipFile = new ZipFile(docx)) {
        java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
        if (entry == null) return com.itextpdf.kernel.geom.PageSize.LETTER;

        javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
        doc.getDocumentElement().normalize();

        org.w3c.dom.NodeList nodeList = doc.getElementsByTagName("w:pgSz");
        if (nodeList.getLength() > 0) {
            org.w3c.dom.Element pgSz = (org.w3c.dom.Element) nodeList.item(nodeList.getLength() - 1);
            String wStr = pgSz.getAttribute("w:w");
            String hStr = pgSz.getAttribute("w:h");
            if (!wStr.isEmpty() && !hStr.isEmpty()) {
                // Word stores dimensions in twentieths of a point (dxa / 20 = points)
                float widthPt = Float.parseFloat(wStr) / 20f;
                float heightPt = Float.parseFloat(hStr) / 20f;
                return new com.itextpdf.kernel.geom.PageSize(widthPt, heightPt);
            }
        }
    } catch (Exception e) {
        // Fallback
    }
    return com.itextpdf.kernel.geom.PageSize.LETTER;
}

	//
     // Extracts top-level paragraph line spacing map (index -> line spacing float) from word/document.xml
     //
    public static java.util.Map<Integer, Float> getTopLevelParagraphLineSpacing(File docx) {
        java.util.Map<Integer, Float> spacingMap = new java.util.HashMap<>();
        try (ZipFile zipFile = new ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) return spacingMap;

            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(zipFile.getInputStream(entry));
            doc.getDocumentElement().normalize();

            org.w3c.dom.Node bodyNode = doc.getElementsByTagName("w:body").item(0);
            if (bodyNode == null) return spacingMap;

            org.w3c.dom.NodeList bodyChildren = bodyNode.getChildNodes();
            int topLevelIndex = 0;
            for (int i = 0; i < bodyChildren.getLength(); i++) {
                org.w3c.dom.Node node = bodyChildren.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String nodeName = node.getNodeName();
                    if ("w:p".equals(nodeName)) {
                        org.w3c.dom.Element p = (org.w3c.dom.Element) node;
                        org.w3c.dom.NodeList pPrs = p.getElementsByTagName("w:pPr");
                        if (pPrs.getLength() > 0) {
                            org.w3c.dom.Element pPr = (org.w3c.dom.Element) pPrs.item(0);
                            org.w3c.dom.NodeList spacings = pPr.getElementsByTagName("w:spacing");
                            if (spacings.getLength() > 0) {
                                org.w3c.dom.Element spacing = (org.w3c.dom.Element) spacings.item(0);
                                String lineStr = spacing.getAttribute("w:line");
                                String lineRuleStr = spacing.getAttribute("w:lineRule");
                                
                                if (lineStr != null && !lineStr.isEmpty()) {
                                    float lineVal = Float.parseFloat(lineStr);
                                    // In Word, if lineRule is "auto", 240 = single spacing, 276 = 1.15 spacing.
                                    // Alternatively, lineVal in twips can be converted to points (/20f).
                                    if ("auto".equals(lineRuleStr) || lineRuleStr.isEmpty()) {
                                        // Word's 'auto' line value is stored in 240ths of a line (e.g. 240 = 1.0, 276 = 1.15)
                                        float multiplier = lineVal / 240f;
                                        spacingMap.put(topLevelIndex, multiplier);
                                    } else {
                                        // Fixed or exact line spacing in twips converted to points
                                        float ptVal = lineVal / 20f;
                                        spacingMap.put(topLevelIndex, ptVal);
                                    }
                                }
                            }
                        }
                        topLevelIndex++;
                    } else if ("w:tbl".equals(nodeName)) {
                        topLevelIndex++;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback silently
        }
        return spacingMap;
    }

}
