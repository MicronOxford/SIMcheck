/* Copyright (c) 2015, Graeme Ball.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/ .
 */

package SIMcheck;

import java.util.*;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

/** ImageJ1 plugin result container using HashMaps: items must have
 * unique names within result types.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class ResultSet {

    // for automatic formatting of result log / output
    private static final int TEXTWIDTH = 52;
    static final int STAT_SIG_FIGS = 3;
    private static final int CHECK_MAX_CHARS = TEXTWIDTH * 100;

    /** Interpretation of a statistic: is it OK? yes, no, maybe. */
    public enum StatOK {

        YES("Yes"),
        NO("No"),
        MAYBE("?"),
        NA("N/A"); // stat has no interpretation -- for info only

        private String desc;
        StatOK(String desc) {
            this.desc = desc;
        }

        public String str() {
            return desc;
        }
    }

    /** Store a numerical statistic and its interpretation. */
    private class Stat {
        Double value;
        StatOK statOK;
        Stat(Double value, StatOK statOK) {
            this.value = value;
            this.statOK = statOK;
        }
    }

    String resultSetName = "";
    String resultSetTLA = "";
    private LinkedHashMap<String, ImagePlus> imps =
            new LinkedHashMap<String, ImagePlus>();
    private LinkedHashMap<String, Stat> stats =
            new LinkedHashMap<String, Stat>();
    private LinkedHashMap<String, String> infos =
            new  LinkedHashMap<String, String>();

    ResultSet(String name, String tla) {
        resultSetName = name;
        resultSetTLA = tla;  // TODO: store ref to ePlugin class instead
    }

    /** Add ImagePlus result & description: title+description MUST be unique. */
    public void addImp(String description, ImagePlus imp) {
        // key composed of image title + description -- separated again later
        description = imp.getTitle() + ":\n" + description;
        if (imps.containsKey(description)) {
            throw new IllegalArgumentException(description + " already exists");
        }
        imps.put(description, imp);
    }

    /** Return Imp number nImp (insert order, 0+) in imps hashmap, else null */
    public ImagePlus getImp(int nImp) {
        Iterator<ImagePlus> it = imps.values().iterator();
        int n = 0;
        while (it.hasNext()) {
            if (n == nImp) {
                return it.next();
            }
        }
        return null;  // did not reach desired nImp
    }

    /**
     * Add a named Double statistic result: statName MUST be unique.
     * NB: it is the plugin's responsibility to decide whether stat is OK.
     */
    public void addStat(String statName, Double value, StatOK statOK) {
        if (stats.containsKey(statName)) {
            throw new IllegalArgumentException(statName + " already exists");
        }
        stats.put(statName, new Stat(value, statOK));
    }

    /** Add an information string with title: title MUST be unique. */
    public void addInfo(String title, String info) {
        if (infos.containsKey(title)) {
            throw new IllegalArgumentException(title + " already exists");
        }
        infos.put(title, info);
    }

    /** Report all results to ImageJ log window. */
    public void report() {
        StringBuilder sb = new StringBuilder(CHECK_MAX_CHARS);
        sb.append("\n");
        sb.append(J.nChars((int)(TEXTWIDTH * 1.0), "-") + "\n");
        sb.append(titleString(resultSetName, " ") + "\n");
        sb.append(J.nChars((int)(TEXTWIDTH * 1.0), "-") + "\n");
        for (Map.Entry<String, ImagePlus> entry : imps.entrySet()) {
            String description = entry.getKey();
            ImagePlus imp = (ImagePlus)entry.getValue();
            sb.append("Displaying " + imp.getTitle() + ":\n");
            int nTitleChars = imp.getTitle().length() + 2;
            description = description.substring(
                    nTitleChars, description.length());
            sb.append(autoFormat(description, (int)(TEXTWIDTH * 1.15), 0));
            sb.append("\n");
            imp.show();
        }
        if (!stats.isEmpty()){
            sb.append("\nStatistics:\n");
        }
        // loop over stats twice: log checked (non-NA) stats first, rest after
        boolean hasCheckedStats = false;
        for (Map.Entry<String, Stat> entry : stats.entrySet()) {
            String statName = entry.getKey();
            if (stats.get(statName).statOK != StatOK.NA) {
                sb.append(statName + " = " + J.d2s(entry.getValue().value));
                sb.append("\n");
                hasCheckedStats = true;
            }
        }
        if (hasCheckedStats) {
            sb.append("--\n");  // print a separator if something to separate
        }
        for (Map.Entry<String, Stat> entry : stats.entrySet()) {
            String statName = entry.getKey();
            if (stats.get(statName).statOK == StatOK.NA) {
                sb.append(statName + " = " + J.d2s(entry.getValue().value));
                sb.append("\n");
            }
        }
        for (Map.Entry<String, String> entry : infos.entrySet()) {
            String infoTitle = entry.getKey();
            String info = entry.getValue();
            if (info.length() > 20) {
                sb.append("\n");  // blank line between infos unless very short
            }
            String sep = ": ";
            if (infoTitle.charAt(infoTitle.length() - 1) == '!') {
                sep = " ";  // don't add a colon if title ends on exclam mark!
            }
            sb.append(infoTitle + sep + autoFormat(info, (int)(TEXTWIDTH * 1.15),
                    infoTitle.length() + 2));
            sb.append("\n");
        }
        IJ.log(stripMultipleBlankLines(sb.toString()));
    }

    /**
     * Display summary Result table of numerical stats and interpretation
     * for all results in a list of ResultSets.
     */
    public static void summary(List<ResultSet> resultSets, String title) {
        ResultsTable rt = new ResultsTable();
        for (ResultSet rs : resultSets) {
            for (String statName : rs.stats.keySet()) {
                // report all stats except where interpretation N/A
                if (rs.stats.get(statName).statOK != StatOK.NA) {
                    rt.incrementCounter();
                    rt.addValue("Check", rs.resultSetTLA);
                    rt.addValue("Statistic", statName);
                    rt.addValue("Value", J.d2s(rs.stats.get(statName).value));
                    rt.addValue("Pass", rs.stats.get(statName).statOK.str());
                }
            }
        }
        rt.show(title);
    }

    /**
     * Automatically add line-breaks, returning text String of fixed width.
     * titleLen shortens the first line, adjusting for length of item title.
     * '  -' starts a new indented list item
     * lists *must* be terminated with '  -- '
     */
    private static String autoFormat(String text, int width, int titleLen) {
        StringBuilder sb = new StringBuilder((int)(text.length() * 1.1));
        int thisSpace = 0;
        int lastSpace = 0;
        int lineStart = 0;
        int maxIter = 100;  // prevent infinite while; implies < maxIter words
        int iter = 0;
        boolean firstLine = true;
        boolean insideList = false;
        boolean hasItemList = false;
        while (thisSpace != -1 && iter < maxIter) {
            lastSpace = thisSpace;
            thisSpace = text.indexOf(" ", lastSpace + 1);
            if (thisSpace == -1) {
                sb.append(text.substring(lineStart, text.length()));
            } else if (thisSpace + 4 < text.length() - 1 &&
                    text.substring(thisSpace, thisSpace + 4).equals("  - ")) {
                insideList = true;
                // handle indentation of list items starting '  -'
                sb.append(text.substring(lineStart, thisSpace) + "\n");
                sb.append("- ");
                lineStart = thisSpace + 4;
                thisSpace += 4;
            } else if (thisSpace + 4 < text.length() &&
                    text.substring(thisSpace, thisSpace + 4).equals("  --")) {
                // end multi-line lists upon ' --'
                hasItemList = true;
                insideList = false;
            } else {
                int adjustedLineStart = lineStart;
                if (firstLine) {
                    // first line, with title, would be too long w/o adjustment
                    adjustedLineStart -= titleLen;
                }
                if (thisSpace - adjustedLineStart > width) {
                    // backtrack to lastSpace
                    sb.append(text.substring(lineStart, lastSpace) + "\n");
                    lineStart = lastSpace + 1;
                    if (insideList) {
                        sb.append("   ");  // extra indent for list items
                    }
                    if (firstLine) {
                        firstLine = false;
                    }
                }
            }
            iter++;
        }
        if (insideList == true) {
            throw new IllegalArgumentException(
                    "malformed text for autoformatting:\n" +
                    " item lists should be terminated with '  -- '");
        }
        if (hasItemList) {
            // trim off "  --" before returning
            return sb.toString().substring(0, sb.length() - 4);
        } else {
            return sb.toString();
        }
    }

    /** Return an Object[] representation of the results. */
    public Object[] objects() {
        List<Object> objList = new ArrayList<Object>();
        objList.add(resultSetName);
        objList.addAll(Arrays.asList(imps.keySet().toArray()));
        objList.addAll(Arrays.asList(imps.values().toArray()));
        objList.addAll(Arrays.asList(stats.keySet().toArray()));
        objList.addAll(Arrays.asList(stats.values().toArray()));
        objList.addAll(Arrays.asList(infos.keySet().toArray()));
        objList.addAll(Arrays.asList(infos.values().toArray()));
        Object[] objArray = objList.toArray();
        return objArray;
    }

    /** Return a String containing 'title' centered in a line of charX. */
    static String titleString(String title, String charX) {
        // trial & error formatting tweaks due to non-fixed-width font :-(
        double textLen = title.length() * 0.83;
        if (!title.equals("")) {
            title = " " + title + " ";
        }
        int nPadChars = (int)(0.5 * (TEXTWIDTH - textLen));
        // char width corrections for IJ's non-fixed-width font
        if (charX.equals("=")) {
            nPadChars = (int)(0.72 * nPadChars);  // correct for wider '='
            if (title.equals("")) {
                nPadChars++;  // minor tweak
            }
        } else if (charX.equals(" ")) {
            nPadChars = (int)(1.9 * nPadChars);  // correct for narrow ' '
        } else if (charX.equals("-")) {
            nPadChars = (int)(0.9 * nPadChars);  // correct for '-' width
        }
        return J.nChars(nPadChars, charX) + title + J.nChars(nPadChars, charX);
    }

    /** Replace occurrences of multiple blank lines with 1 blank line. */
    private static String stripMultipleBlankLines(String s) {
        return s.replaceAll("\n{3,}", "\n\n");
    }

    /** test method */
    public static void main(String[] args) {

        ResultSet results = new ResultSet("ResultSet Test", "TST");
        new ImageJ();
        ImagePlus blimp = TestData.lawn;
        results.addImp("a bead lawn", blimp);
        results.addStat("stat1, imWidth",
                (double)blimp.getWidth(), StatOK.YES);
        results.addStat("stat2, imHeight",
                (double)blimp.getHeight(), StatOK.YES);
        results.addStat("stat3, imBytesPerPix",
                (double)blimp.getBytesPerPixel(), StatOK.YES);
        results.addInfo("about", "this is raw SIM data for a bead lawn");
        results.addInfo("info list", "list items auto-formatted:"
                + "  - item 1  - item 2  - item 3"
                + "  - item 4: long item exceeding TEXTWIDTH that tests"
                + " multi-line wrap indentation"
                + "  -- ");  // '  -- ' teminated

        IJ.log("report()  - should show all images and log"
                + " all stats, info. Check stats appear in order.");
        results.report();

        IJ.log("\nchecking identity of " + results.objects().length
                + " (expected " + 11 + ") returned objects...");
        Object[] objs = results.objects();
        IJ.log("resultSetName, objs[0] instanceof String? "
                + new Boolean(objs[0] instanceof String).toString());
        IJ.log("imps key 1, objs[1] instanceof String? "
                + new Boolean(objs[1] instanceof String).toString());
        IJ.log("imps value 1, objs[2] instanceof ImagePlus? "
                + new Boolean(objs[2] instanceof ImagePlus).toString());
        IJ.log("stats key 1, objs[3] instanceof String? "
                + new Boolean(objs[3] instanceof String).toString());
        IJ.log("stats key 2, objs[4] instanceof String? "
                + new Boolean(objs[4] instanceof String).toString());
        IJ.log("stats key 3, objs[5] instanceof String? "
                + new Boolean(objs[5] instanceof String).toString());
        IJ.log("stats value 1, objs[6] instanceof Double? "
                + new Boolean(((Stat)objs[6]).value instanceof Double).toString());
        IJ.log("stats value 2, objs[7] instanceof Double? "
                + new Boolean(((Stat)objs[7]).value instanceof Double).toString());
        IJ.log("stats value 3, objs[8] instanceof Double? "
                + new Boolean(((Stat)objs[8]).value instanceof Double).toString());
        IJ.log("infos key 1, objs[9] instanceof String? "
                + new Boolean(objs[9] instanceof String).toString());
        IJ.log("infos value 1, objs[10] instanceof String? "
                + new Boolean(objs[10] instanceof String).toString());

        IJ.log("addStat() duplicate throws IllegalArgument exception?");
        try {
            results.addStat("stat1, imWidth", 7.0d, StatOK.YES);
            IJ.log("NO EXCEPTION");
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                IJ.log("YES");
            } else {
                IJ.log("WRONG EXCEPTION");
            }
        }
    }
}
