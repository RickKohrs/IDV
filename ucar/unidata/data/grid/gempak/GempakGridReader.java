/*
 * $Id: IDV-Style.xjs,v 1.3 2007/02/16 19:18:30 dmurray Exp $
 *
 * Copyright 1997-2007 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */


package ucar.unidata.data.grid.gempak;

import ucar.unidata.io.RandomAccessFile;
import ucar.unidata.util.StringUtil;

import java.io.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Read a Gempak grid file
 *
 * @author IDV Development Team
 * @version $Revision: 1.3 $
 */
public class GempakGridReader extends GempakFileReader {

    /** Grid identifier */
    public static final String GRID = "GRID";

    /** Grid analysis block identifier */
    public static final String ANLB = "ANLB";

    /** Grid nav block identifier */
    public static final String NAVB = "NAVB";

    /** Navigation Block */
    private NavigationBlock navBlock;

    /** Navigation Block */
    private AnalysisBlock analBlock;

    /** Grid index */
    private GridIndex gridIndex;

    /** column headers */
    private static final String[] kcolnm = {
        "GDT1", "GTM1", "GDT2", "GTM2", "GLV1", "GLV2", "GVCD", "GPM1",
        "GPM2", "GPM3"
    };

    /** grid header len */
    private int khdrln = 0;

    /**
     * Bean ctor
     */
    public GempakGridReader() {}

    /**
     * Create a Gempak Grid Reader from the file
     *
     * @param filename  filename
     *
     * @throws IOException problem reading file
     */
    public GempakGridReader(String filename) throws IOException {
        super(filename);
    }

    /**
     * Create a Gempak Grid Reader from the file
     *
     * @param raf  RandomAccessFile
     *
     * @throws IOException problem reading file
     */
    public GempakGridReader(RandomAccessFile raf) throws IOException {
        super(raf);
    }

    /**
     * Initialize this reader.  Read all the metadata
     *
     * @return true if successful
     *
     * @throws IOException  problem reading the data
     */
    protected boolean init() throws IOException {
        return init(true);
    }

    /**
     * Initialize this reader.  Get the Grid specific info
     *
     * @param fullCheck  check to make sure there are grids we can handle
     * @return true if successful
     *
     * @throws IOException  problem reading the data
     */
    protected boolean init(boolean fullCheck) throws IOException {

        if ( !super.init(fullCheck)) {
            return false;
        }
        gridIndex = new GridIndex();

        // Modeled after GD_OFIL
        if (dmLabel.kftype != MFGD) {
            logError("not a grid file ");
            return false;
        }
        // find the part for GRID
        DMPart part = getPart("GRID");

        if (part == null) {
            logError("No part named GRID found");
            return false;
        }
        int lenhdr = part.klnhdr;
        if (lenhdr > LLGDHD) {
            logError("Grid part header too long");
            return false;
        }
        khdrln = lenhdr - 2;

        // check that the column names are correct
        for (int i = 0; i < keys.kkcol.size(); i++) {
            String colkey = keys.kkcol.get(i);
            if ( !colkey.equals(kcolnm[i])) {
                logError("Column name " + colkey + " doesn't match "
                         + kcolnm[i]);
                return false;
            }
        }
        if (!fullCheck) return true;

        // Make the NAV and ANAL blocks
        float[] headerArray = getFileHeader(NAVB);
        if (headerArray == null) {
            return false;
        }
        navBlock = new NavigationBlock(headerArray);
        //System.out.println("nav = " + navBlock);
        gridIndex.addHorizCoordSys(navBlock);

        headerArray = getFileHeader(ANLB);
        if (headerArray == null) {
            return false;
        }
        analBlock = new AnalysisBlock(headerArray);

        // Make the grid headers
        // TODO: move this up into GempakFileReader using DM_RHDA
        // and account for the flipping there.
        List<GempakGridRecord>  tmpList = new ArrayList<GempakGridRecord>();
        int   iword   = dmLabel.kpcolh;
        int[] header  = new int[dmLabel.kckeys];
        for (int i = 0; i < dmLabel.kcol; i++) {
            int valid = DM_RINT(iword++);
            DM_RINT(iword, header);
            if (valid != IMISSD) {
                // swap the appropriate strings
                // vertical coord if stored as a string
                if (header[6] > GempakUtil.vertCoords.length && needToSwap) {
                    header[6] = GempakUtil.swp4(header[6]);
                }
                if (needToSwap) {
                    GempakUtil.swp4(header, 7, 3);
                }
                GempakGridRecord gh = new GempakGridRecord(i + 1, header);
                gh.navBlock = navBlock;
                String name = gh.getParameterName();
                //if (name.equals("TMPK") ||
                //    name.equals("UREL") ||
                //    name.equals("VREL") ||
                //    name.equals("PMSL")) {
                tmpList.add(gh);
                //}
            }
            iword += header.length;
        }

        // find the packing types for these grids
        // TODO: go back to using gridList
        //List gridList = gridIndex.getGridRecords();
        //if ( !gridList.isEmpty()) {
        if ( !tmpList.isEmpty()) {
            for (int i = 0; i < tmpList.size(); i++) {
                GempakGridRecord gh = (GempakGridRecord) tmpList.get(i);
                gh.packingType = getGridPackingType(gh.gridNumber);
                if ((gh.packingType == MDGGRB) || (gh.packingType == MDGRB2)
                        || (gh.packingType == MDGNON)) {
                    gridIndex.addGridRecord(gh);
                }
            }
        } else {
            return false;
        }
        // check to see if there are any grids that we can handle
        if (gridIndex.getGridRecords().isEmpty()) {
            return false;
        }

        return true;

    }

    /**
     * Run the program
     *
     * @param args  filename
     *
     * @throws IOException problem reading the file
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("need to supply a GEMPAK grid file name");
            System.exit(1);
        }

        GempakGridReader ggr = new GempakGridReader(args[0]);
        ggr.listGrids();
        String var = "PMSL";
        if (args.length > 1) {
            var = args[1];
        }
        GempakGridRecord gh = ggr.findGrid(var);
        if (gh != null) {
            System.out.println("\n" + gh);
            float[] data = ggr.readGrid(gh);
            if (data != null) {
                System.out.println("# of points = " + data.length);
                int   cnt = 0;
                int   it  = 10;
                float min = Float.POSITIVE_INFINITY;
                float max = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < data.length; i++) {
                    if (cnt == it) {
                        cnt = 0;
                    }
                    cnt++;
                    if ((data[i] != RMISSD) && (data[i] < min)) {
                        min = data[i];
                    }
                    if ((data[i] != RMISSD) && (data[i] > max)) {
                        max = data[i];
                    }
                }
                System.out.println("max/min = " + max + "/" + min);
            } else {
                System.out.println("unable to decode grid data");
            }
        }
        /*
        */
    }

    /**
     * Get the grid index
     * @return the GridIndex
     */
    public GridIndex getGridIndex() {
        return gridIndex;
    }

    /**
     * Get the grid packing type
     *
     * @param gridNumber   grid number
     *
     * @return packing type or error number
     *
     * @throws IOException problem reading file
     */
    public int getGridPackingType(int gridNumber) throws IOException {
        // See DM_RDTR
        int irow = 1;  // Always 1 for grids
        int icol = gridNumber;
        if ((icol < 1) || (icol > dmLabel.kcol)) {
            System.out.println("bad grid number " + icol);
            return -9;
        }
        int iprt = getPartNumber("GRID");
        if (iprt == 0) {
            System.out.println("couldn't find part");
            return -10;
        }
        // gotta subtract 1 because parts are 1 but List is 0 based
        DMPart part = (DMPart) parts.get(iprt - 1);
        // check for valid data type
        if (part.ktyprt != MDGRID) {
            System.out.println("Not a valid type");
            return -21;
        }
        int ilenhd = part.klnhdr;
        int ipoint = dmLabel.kpdata
                     + (irow - 1) * dmLabel.kcol * dmLabel.kprt
                     + (icol - 1) * dmLabel.kprt + (iprt - 1);
        // From DM_RPKG
        int istart = DM_RINT(ipoint);
        if (istart == 0) {
            return -15;
        }
        int length = DM_RINT(istart);
        int isword = istart + 1;
        if (length <= ilenhd) {
            System.out.println("length (" + length
                               + ") is less than header length (" + ilenhd
                               + ")");
            return -15;
        } else if (Math.abs(length) > 10000000) {
            System.out.println("length is huge");
            return -34;
        }
        int[] header = new int[ilenhd];
        DM_RINT(isword, header);
        int nword = length - ilenhd;
        isword += ilenhd;
        // read the data packing type
        int ipktyp = DM_RINT(isword);
        return ipktyp;
    }

    /**
     * Find the first grid with this name
     *
     * @param parm  name of grid
     *
     * @return  the grid header or null
     */
    public GempakGridRecord findGrid(String parm) {
        List gridList = gridIndex.getGridRecords();
        if (gridList == null) {
            return null;
        }
        for (int i = 0; i < gridList.size(); i++) {
            GempakGridRecord gh = (GempakGridRecord) gridList.get(i);
            if (gh.param.trim().equals(parm)) {
                return gh;
            }
        }
        return null;
    }

    /**
     * Get the grid packing type
     *
     * @param gr  grid record
     *
     * @return packing type or error number
     *
     * @throws IOException problem reading file
     */
    public float[] readGrid(GridRecord gr) throws IOException {

        int     gridNumber = ((GempakGridRecord) gr).getGridNumber();
        float[] data       = null;
        // See DM_RDTR
        int irow = 1;  // Always 1 for grids
        int icol = gridNumber;
        if ((icol < 1) || (icol > dmLabel.kcol)) {
            System.out.println("bad grid number " + icol);
            return null;
        }
        int iprt = getPartNumber("GRID");
        if (iprt == 0) {
            System.out.println("couldn't find part");
            return null;
        }
        // gotta subtract 1 because parts are 1 but List is 0 based
        DMPart part = (DMPart) parts.get(iprt - 1);
        // check for valid data type
        if (part.ktyprt != MDGRID) {
            System.out.println("Not a valid type");
            return null;
        }
        int ilenhd = part.klnhdr;
        int ipoint = dmLabel.kpdata
                     + (irow - 1) * dmLabel.kcol * dmLabel.kprt
                     + (icol - 1) * dmLabel.kprt + (iprt - 1);
        // From DM_RDTR
        int istart = DM_RINT(ipoint);
        if (istart == 0) {
            return null;
        }
        int length = DM_RINT(istart);
        int isword = istart + 1;
        if (length <= ilenhd) {
            System.out.println("length (" + length
                               + ") is less than header length (" + ilenhd
                               + ")");
            return null;
        } else if (Math.abs(length) > 10000000) {
            System.out.println("length is huge");
            return null;
        }
        int[] header = new int[ilenhd];
        DM_RINT(isword, header);
        int nword = length - ilenhd;
        isword += header.length;

        // from DM_RPKG
        // read the data packing type
        int ipktyp = DM_RINT(isword);
        int iiword = isword + 1;
        int lendat = nword - 1;
        if (ipktyp == MDGNON) {  // no packing
            data = new float[lendat];
            DM_RFLT(iiword, data);
            return data;
        }
        int iiw;
        int irw;
        if (ipktyp == MDGDIF) {
            iiw = 4;
            irw = 3;
        } else if (ipktyp == MDGRB2) {
            iiw = 4;
            irw = 1;
        } else {
            iiw = 3;
            irw = 2;
        }
        int[]   iarray = new int[iiw];
        float[] rarray = new float[irw];
        DM_RINT(iiword, iarray);
        iiword = iiword + iiw;
        lendat = lendat - iiw;
        DM_RFLT(iiword, rarray);
        iiword = iiword + irw;
        lendat = lendat - irw;
        int decimalScale = gr.getDecimalScale();

        if (ipktyp == MDGRB2) {
            data = unpackGrib2Data(iiword, lendat, iarray, rarray);
            return data;
        }
        int     nbits  = iarray[0];
        int     misflg = iarray[1];
        boolean miss   = misflg != 0;
        int     kxky   = iarray[2];
        int     mword  = kxky;
        int     kx     = 0;
        if (iiw == 4) {
            kx = iarray[3];
        }
        float ref    = rarray[0];
        float scale  = rarray[1];
        float difmin = 0;
        if (irw == 3) {
            difmin = rarray[2];
        }
        data = unpackData(iiword, lendat, ipktyp, kxky, nbits, ref, scale,
                          miss, difmin, kx, decimalScale);
        return data;

    }

    /**
     * Read packed data
     *
     * @param iiword         Starting word  (FORTRAN 1 based)
     * @param nword          Number of words
     * @param ipktyp         Packing type
     * @param kxky           Number of grid points
     * @param nbits          Number of bits
     * @param ref            Reference minimum value of grid
     * @param scale          Scaling factor
     * @param miss           Missing data flag
     * @param difmin         Minimum value of differences
     * @param kx             Number of points in x direction
     * @param decimalScale   scale of the values
     *
     * @return   unpacked data
     *
     * @throws IOException problem reading file
     */
    private synchronized float[] unpackData(int iiword, int nword,
                                            int ipktyp, int kxky, int nbits,
                                            float ref, float scale,
                                            boolean miss, float difmin,
                                            int kx, int decimalScale)
            throws IOException {
        if (ipktyp == MDGGRB) {
            return unpackGrib1Data(iiword, nword, kxky, nbits, ref, scale,
                                   miss, decimalScale);
            //return unpackGrib1DataG(iiword, nword, kxky, nbits, ref, scale, miss);
        } else if (ipktyp == MDGNMC) {
            return null;
        } else if (ipktyp == MDGDIF) {
            return null;
        }
        return null;
    }


    /**
     * Read packed Grib1 data using ucar.grib code
     * @param iiword  Starting word  (FORTRAN 1 based)
     * @param nword   number of words
     * @param kxky    size of grid (kx*ky)
     * @param nbits   number of bits per word
     * @param ref     reference value
     * @param scale   scale value
     * @param miss    replace missing
     * @param decimalScale   scale of the values
     * @return   unpacked data
     *
     * @throws IOException problem reading file
     */
    private float[] unpackGrib1Data(int iiword, int nword, int kxky,
                                    int nbits, float ref, float scale,
                                    boolean miss, int decimalScale)
            throws IOException {
        //System.out.println("decimal scale = " + decimalScale);
        float[] values = new float[kxky];
        bitPos = 0;
        bitBuf = 0;
        next   = 0;
        ch1    = 0;
        ch2    = 0;
        ch3    = 0;
        ch4    = 0;
        rf.seek(getOffset(iiword));
        int   idat;
        float scaleFactor = (float) Math.pow(10.0, -decimalScale);
        for (int i = 0; i < values.length; i++) {
            idat = bits2UInt(nbits);
            if (miss && (idat == IMISSD)) {
                values[i] = IMISSD;
            } else {
                values[i] = (ref + scale * idat) * scaleFactor;
            }
            /*
            if (i < 25) {
                System.out.println("values[" + i + "] = " + values[i]);
            }
            */
        }
        return values;
    }

    /**
     * Read packed Grib2 data
     *
     * @param iiword  Starting word  (FORTRAN 1 based)
     * @param lendat  Number of words
     * @param iarray  integer packing info
     * @param rarray  float packing info
     * @return   unpacked data
     *
     * @throws IOException problem reading file
     */
    private float[] unpackGrib2Data(int iiword, int lendat, int[] iarray,
                                    float[] rarray)
            throws IOException {

        long            start    = getOffset(iiword);
        GempakGrib2Data gemGrib2 = new GempakGrib2Data(rf);
        float[]         data     = gemGrib2.getData(start);
        if ((( iarray[3] >> 6 ) & 1) == 0) { // -y scanning - flip
            data = gb2_ornt(iarray[1],iarray[2],iarray[3],data);
        }
        return data;
    }

    /**
     * Print out the navibation block so it looks something like this:
     * <pre>
     *   GRID NAVIGATION:
     *        PROJECTION:          LCC
     *        ANGLES:                25.0   -95.0    25.0
     *        GRID SIZE:           93  65
     *        LL CORNER:              12.19   -133.46
     *        UR CORNER:              57.29    -49.38
     * </pre>
     */
    public void printNavBlock() {
        StringBuffer buf = new StringBuffer("GRID NAVIGATION:");
        if (navBlock != null) {
            buf.append(navBlock.toString());
        } else {
            buf.append("\n\tUNKNOWN GRID NAVIGATION");
        }
        System.out.println(buf.toString());
    }

    /**
     * Print out the analysis block so it looks something like this:
     */
    public void printAnalBlock() {
        StringBuffer buf = new StringBuffer("GRID ANALYSIS BLOCK:");
        if (analBlock != null) {
            buf.append(analBlock.toString());
        } else {
            buf.append("\n\tUNKNOWN ANALYSIS TYPE");
        }
        System.out.println(buf.toString());
    }

    /**
     * Get list of grids
     * @return list of grids
     */
    public List<GridRecord> getGridList() {
        return gridIndex.getGridRecords();
    }

    /**
     * Print out the grids.
     */
    public void printGrids() {
        List gridList = gridIndex.getGridRecords();
        if (gridList == null) {
            return;
        }
        System.out.println(
            "  NUM       TIME1              TIME2           LEVL1 LEVL2  VCORD PARM");
        for (Iterator iter = gridList.iterator(); iter.hasNext(); ) {
            System.out.println(iter.next());
        }
    }

    /**
     * List out the grids (aka GDINFO)
     */
    public void listGrids() {
        List gridList = gridIndex.getGridRecords();
        System.out.println("\nGRID FILE: " + getFilename() + "\n");
        printNavBlock();
        System.out.println("");
        printAnalBlock();
        System.out.println("\nNumber of grids in file:  " + gridList.size());
        System.out.println("\nMaximum number of grids in file:  "
                           + dmLabel.kcol);
        System.out.println("");
        printGrids();
    }

    /** bit position */
    int bitPos = 0;

    /** bit buffer */
    int bitBuf = 0;

    /** bit buffer size */
    int next = 0;

    /** character 1 */
    private int ch1 = 0;

    /** character 2 */
    private int ch2 = 0;

    /** character 3 */
    private int ch3 = 0;

    /** character 4 */
    private int ch4 = 0;

    /**
     * Convert bits (nb) to Unsigned Int .
     *
     * @param nb  number of bits
     * @throws IOException
     * @return int of BinaryDataSection section
     */
    private int bits2UInt(int nb) throws IOException {
        int bitsLeft = nb;
        int result   = 0;

        if (bitPos == 0) {
            //bitBuf = raf.read();
            getNextByte();
            bitPos = 8;
        }

        while (true) {
            int shift = bitsLeft - bitPos;
            if (shift > 0) {
                // Consume the entire buffer
                result   |= bitBuf << shift;
                bitsLeft -= bitPos;

                // Get the next byte from the RandomAccessFile
                //bitBuf = raf.read();
                getNextByte();
                bitPos = 8;
            } else {
                // Consume a portion of the buffer
                result |= bitBuf >> -shift;
                bitPos -= bitsLeft;
                bitBuf &= 0xff >> (8 - bitPos);  // mask off consumed bits

                return result;
            }
        }                                        // end while
    }                                            // end bits2Int

    /**
     * Get the next byte
     *
     * @throws IOException problem reading the byte
     */
    public void getNextByte() throws IOException {
        if ( !needToSwap) {
            // Get the next byte from the RandomAccessFile
            bitBuf = rf.read();
        } else {
            if (next == 3) {
                bitBuf = ch3;
            } else if (next == 2) {
                bitBuf = ch2;
            } else if (next == 1) {
                bitBuf = ch1;
            } else {
                ch1    = rf.read();
                ch2    = rf.read();
                ch3    = rf.read();
                ch4    = rf.read();
                bitBuf = ch4;
                next   = 4;
            }
            next--;
        }
    }

/************************************************************************
 * gb2_ornt                                                             *
 *                                                                      *
 * This function checks the fields scanning mode flags and re-orders    *
 * the grid point values so that they traverse left to right for each   *
 * row starting at the bottom row.                                      *
 *                                                                      *
 * gb2_ornt ( kx, ky, scan_mode, ingrid, fgrid, iret )                  *
 *                                                                      *
 * Input parameters:                                                    *
 *      kx              int             Number of columns               *
 *      ky              int             Number of rows                  *
 *      scan_mode       int             GRIB2 scanning mode flag        *
 *      *ingrid         float           unpacked GRIB2 grid data        *
 *                                                                      *
 * Output parameters:                                                   *
 *  *fgrid      float   Unpacked grid data                              *
 *  *iret       int             Return code                             *
 *                       -40 = scan mode not implemented                *
 **                                                                     *
 * Log:                                                                 *
 * S. Gilbert            1/04                                           *
 ***********************************************************************/
private float[] gb2_ornt ( int kx, int ky, int scan_mode, float[] ingrid) 
{
    float[] fgrid = new float[ingrid.length];

    int ibeg, jbeg, iinc, jinc, itmp;
    int icnt, jcnt, kcnt, idxarr;

    int idrct, jdrct, consec, boustr;


/*---------------------------------------------------------------------*/

    idrct  = ( scan_mode >> 7 ) & 1;
    jdrct  = ( scan_mode >> 6 ) & 1;
    consec = ( scan_mode >> 5 ) & 1;
    boustr = ( scan_mode >> 4 ) & 1;

    if ( idrct == 0 ) {
        ibeg = 0;
        iinc = 1;
    }
    else {
        ibeg = kx - 1;
        iinc = -1;
    }
    if ( jdrct == 1 ) {
        jbeg = 0;
        jinc = 1;
    }
    else {
        jbeg = ky - 1;
        jinc = -1;
    }

    kcnt = 0;
    if ( consec == 1 && boustr == 0 ) {
        /*  adjacent points in same column;  each column same direction  */
        for ( jcnt=jbeg; (0<=jcnt&&jcnt<ky); jcnt+=jinc ) {
            for ( icnt=ibeg; (0<=icnt&&icnt<kx); icnt+=iinc ) {
               idxarr = ky * icnt + jcnt;
               fgrid[kcnt] = ingrid[idxarr];
               kcnt++;
            }
        }
    }
    else if( consec == 0 && boustr == 0 ) {
        /*  adjacent points in same row;  each row same direction  */
        for ( jcnt=jbeg; (0<=jcnt&&jcnt<ky); jcnt+=jinc ) {
            for ( icnt=ibeg; (0<=icnt&&icnt<kx); icnt+=iinc ) {
               idxarr = kx * jcnt + icnt;
               fgrid[kcnt] = ingrid[idxarr];
               kcnt++;
            }
        }
    }
    else if ( consec == 1 && boustr == 1 ) {
        /*  adjacent points in same column; each column alternates direction */
        for ( jcnt=jbeg; (0<=jcnt&&jcnt<ky); jcnt+=jinc ) {
            itmp=jcnt;
            if (idrct == 1 && kx%2 == 0 ) itmp = ky - jcnt - 1;
            for ( icnt=ibeg; (0<=icnt&&icnt<kx); icnt+=iinc ) {
               idxarr = ky * icnt + itmp;
               fgrid[kcnt] = ingrid[idxarr];
               itmp = ( itmp != jcnt ) ? jcnt : ky - jcnt - 1;  /* toggle */
               kcnt++;
            }
        }
    }
    else if( consec == 0 && boustr == 1 ) {
        /*  adjacent points in same row;  each row alternates direction  */
        if ( jdrct == 0 ) {
           if (idrct == 0 && ky%2 == 0) {
              ibeg = kx - 1;
              iinc = -1;
           } 
           if (idrct == 1 && ky%2 == 0) {
              ibeg = 0;
              iinc = 1;
           } 
        }
        for ( jcnt=jbeg; (0<=jcnt&&jcnt<ky); jcnt+=jinc ) {
            for ( icnt=ibeg; (0<=icnt&&icnt<kx); icnt+=iinc ) {
               idxarr = kx * jcnt + icnt;
               fgrid[kcnt] = ingrid[idxarr];
               kcnt++;
            } 
            ibeg = ( ibeg != 0 ) ? 0 : kx - 1;         /* toggle */
            iinc = ( iinc != 1 ) ? 1 : - 1;         /* toggle */
        }      
    } else {
        fgrid = ingrid;
    } 
    return fgrid;
    
}       

}

