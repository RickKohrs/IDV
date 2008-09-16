/*
 * $Id: YahooLocationControl.java,v 1.3 2006/12/01 20:16:39 jeffmc Exp $
 *
 * Copyright  1997-2004 Unidata Program Center/University Corporation for
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

package ucar.unidata.idv.control;


import org.w3c.dom.Element;

import ucar.unidata.data.text.GempakTextProductDataSource;
import ucar.unidata.data.text.ProductGroup;
import ucar.unidata.data.text.Product;


import ucar.unidata.collab.Sharable;

import ucar.unidata.data.DataChoice;
import ucar.unidata.data.DataInstance;

import ucar.unidata.geoloc.Bearing;

import ucar.unidata.gis.SpatialGrid;
import ucar.unidata.gis.WorldWindReader;


import ucar.unidata.idv.DisplayConventions;


import ucar.unidata.metdata.NamedStationImpl;
import ucar.unidata.metdata.NamedStationTable;
import ucar.unidata.ui.symbol.*;

import ucar.unidata.util.FileManager;

import ucar.unidata.util.GuiUtils;

import ucar.unidata.util.IOUtil;
import ucar.unidata.util.Misc;

import ucar.unidata.util.ObjectListener;
import ucar.unidata.util.StringUtil;

import ucar.unidata.util.TwoFacedObject;
import ucar.unidata.view.geoloc.NavigatedDisplay;
import ucar.unidata.xml.XmlUtil;

import ucar.visad.display.*;

import ucar.visad.display.CompositeDisplayable;
import ucar.visad.display.StationLocationDisplayable;
import ucar.visad.display.StationModelDisplayable;


import visad.*;


import visad.georef.*;

import visad.georef.EarthLocation;
import visad.georef.EarthLocation;
import visad.georef.LatLonPoint;
import visad.georef.NamedLocation;
import visad.georef.NamedLocationTuple;


import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;


import java.io.File;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.swing.*;
import javax.swing.border.*;




/**
 * Class to display a set of locations
 *
 * @author MetApps Development Team
 * @version $Revision: 1.3 $ $Date: 2006/12/01 20:16:39 $
 */


public class TextProductControl extends StationLocationControl {

    private GempakTextProductDataSource dataSource;

    private List<ProductGroup> productGroups;

    private JComboBox productGroupCbx;
    private JComboBox productCbx;
    private ProductGroup productGroup;
    private Product product;
    private JTextArea textArea;
    private JLabel stationLabel;

    private boolean ignoreEvents = false;

    private NamedStationTable stationTable;

    /**
     * Default cstr;
     */
    public TextProductControl() {}


    /**
     * Make the gui
     *
     * @return The gui
     *
     * @throws RemoteException On badness
     * @throws VisADException On badness
     */
    protected Container doMakeContents()
            throws VisADException, RemoteException {
        JTabbedPane tabs = doMakeTabs(false,false);

        setCenterOnClick(false);
        //        setDeclutter(false);
        JComponent contents = null;
        textArea = new JTextArea("",30,80);
        productGroupCbx = new JComboBox(new Vector(productGroups));



        if(productGroup!=null) {
            productGroupCbx.setSelectedItem(productGroup);
        } else {
            productGroup = (ProductGroup)productGroupCbx.getSelectedItem();
        }
        productCbx = new JComboBox();

        GuiUtils.tmpInsets = GuiUtils.INSETS_5;
        JComponent productComp = GuiUtils.doLayout(new Component[]{
                GuiUtils.rLabel("Group:"),
                GuiUtils.left(productGroupCbx),
                GuiUtils.rLabel("Product:"),
                GuiUtils.left(productCbx),
                GuiUtils.rLabel("Station:"),
                stationLabel = new JLabel(" ")
            },2, GuiUtils.WT_NY, GuiUtils.WT_N);
        contents = GuiUtils.topCenter(productComp,GuiUtils.makeScrollPane(textArea,200,400));
        updateProducts();
        if(product!=null) {
            productCbx.setSelectedItem(product);
        } else {
            product = (Product)productCbx.getSelectedItem();
        }
        productGroupCbx.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(ignoreEvents) return;
                productGroup = (ProductGroup)productGroupCbx.getSelectedItem();
                updateProducts();
            }
            });


        productCbx.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(ignoreEvents) return;
                Product selectedProduct  =(Product)productCbx.getSelectedItem(); 
                if(!Misc.equals(product, selectedProduct)) {
                    product = selectedProduct;
                    updateText();
                }
            }
            });


        updateText();
        tabs.insertTab("Products", null, contents,"",0);
        tabs.setSelectedIndex(0);
        return tabs;
    }



    private NamedStationTable currentTable;
    private     List stationList = new ArrayList();

    protected List getStationList() {
        return stationList;
    }


    NamedStationImpl selectedStation;
    protected void     selectedStationsChanged(List selectionList) {
        if(selectionList.size()==0) {
            selectedStation = null;
        } else {
            selectedStation = (NamedStationImpl) selectionList.get(0);
        }

        if(selectedStation!=null) {
            stationLabel.setText(selectedStation.getID() + ":" +selectedStation.getName());
        }
    }

    public void updateText() {
        String text = "";
        NamedStationTable newTable = dataSource.getStations(product);

        if(newTable!=currentTable) {
            if(newTable!=null) {
                stationList = new ArrayList(newTable.values());
            } else {
                stationList = new ArrayList();
            }
            selectedStation = null;
            stationLabel.setText("");
            loadData();
            currentTable = newTable;
            textArea.setText("");
        }


        if(product!=null) {
            //            text = dataSource.readProduct(product);
        }
        textArea.setText(text);
    }


    public void updateProducts() {
        ignoreEvents = true;
        try {
            if(productGroup!=null) {
                GuiUtils.setListData(productCbx, productGroup.getProducts().toArray());
                product = (Product)productCbx.getSelectedItem();
                updateText();
            }
        } finally {
            ignoreEvents = false;
        }
    }

    /**
     * @param dataChoice    the DataChoice of the moment -
     *
     * @return  true if successful
     *
     * @throws  VisADException  there was a VisAD error
     * @throws  RemoteException  there was a remote error
     */
    public boolean init(DataChoice dataChoice)
            throws VisADException, RemoteException {
        List dataSources = new ArrayList();
        dataChoice.getDataSources(dataSources);

        if (dataSources.size() != 1) {
            userMessage("Could not find Text Product  Data Source");
            return false;
        }

        if ( !(dataSources.get(0) instanceof GempakTextProductDataSource)) {
            userMessage("Could not find Text Product  Data Source");
            return false;
        }

        dataSource = (GempakTextProductDataSource)dataSources.get(0);
        productGroups = dataSource.getProductGroups();
        if(productGroup!=null) {
            int idx = productGroups.indexOf(productGroup);
            if(idx>=0)
                productGroup = productGroups.get(idx);
            else 
                productGroup = null;
        }
        return super.init(dataChoice);
    }



    /**
       Set the ProductGroup property.

       @param value The new value for ProductGroup
    **/
    public void setTextProductGroup (ProductGroup value) {
	productGroup = value;
    }

    /**
       Get the ProductGroup property.

       @return The ProductGroup
    **/
    public ProductGroup getTextProductGroup () {
	return productGroup;
    }

    /**
       Set the Product property.

       @param value The new value for Product
    **/
    public void setTextProduct (Product value) {
	product = value;
    }

    /**
       Get the Product property.

       @return The Product
    **/
    public Product getTextProduct () {
	return product;
    }


}

