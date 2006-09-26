/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.web.templating.xforms.schemabuilder;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.Pointer;
import org.apache.xerces.xs.*;
import org.chiba.xml.util.DOMUtil;
import org.chiba.xml.xforms.NamespaceCtx;
import org.w3c.dom.*;
import org.w3c.dom.ls.*;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.xml.sax.InputSource;
import org.alfresco.web.templating.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/*
 * Search for TODO for things remaining to-do in this implementation.
 *
 * TODO: Support configuration mechanism to allow properties to be set without programming.
 * TODO: i18n/l10n of messages, hints, captions. Possibly leverage org.chiba.i18n classes.
 * TODO: When Chiba supports itemset, use schema keyref and key constraints for validation.
 * TODO: Support namespaces in instance documents. Currently can't do this due to Chiba bugs.
 * TODO: Place default values for list and enumeration types at the beginning of the item list.
 *
 */

/**
 * An abstract implementation of the SchemaFormBuilder interface allowing
 * an XForm to be automatically generated for an XML Schema definition.
 * This abstract class implements the buildForm and buildFormAsString methods
 * and associated helper but relies on concrete subclasses to implement other
 * required interface methods (createXXX, startXXX, and endXXX methods).
 *
 * @author $Author: unl $
 * @version $Id: AbstractSchemaFormBuilder.java,v 1.25 2005/03/29 14:12:06 unl Exp $
 */
public abstract class AbstractSchemaFormBuilder 
    implements SchemaFormBuilder {

    ////////////////////////////////////////////////////////////////////////////

    private final Comparator typeExtensionSorter = new Comparator() 
    {
	public int compare(Object obj1, Object obj2) 
	{
	    if (obj1 == null && obj2 != null)
		return -1;
	    else if (obj1 != null && obj2 == null)
		return 1;
	    else if (obj1 == obj2 || (obj1 == null && obj2 == null))
		return 0;
	    else 
	    {
		try
		{
		    final XSTypeDefinition type1 = (XSTypeDefinition) obj1;
		    final XSTypeDefinition type2 = (XSTypeDefinition) obj2;
		    return (type1.derivedFromType(type2, XSConstants.DERIVATION_EXTENSION)
			    ? 1
			    : (type2.derivedFromType(type1, XSConstants.DERIVATION_EXTENSION)
			       ? -1
			       : 0));
		}
		catch (ClassCastException ex) 
		{
		    String s = "ClassCastException in typeExtensionSorter: one of the types is not a type !";
		    s = s + "\n obj1 class = " + obj1.getClass().getName() + ", toString=" + obj1.toString();
		    s = s + "\n obj2 class = " + obj2.getClass().getName() + ", toString=" + obj2.toString();
		    SchemaFormBuilder.LOGGER.error(s, ex);
		    return 0;
		}
	    }
	}
    };

    private static final String PROPERTY_PREFIX =
            "http://www.chiba.org/properties/schemaFormBuilder/";
    /**
     * Property to control the cascading style sheet used for the XForm - corresponds to envelope@chiba:css-style.
     */
    public static final String CSS_STYLE_PROP =
            PROPERTY_PREFIX + "envelope@css-style";
    private static final String DEFAULT_CSS_STYLE_PROP = "style.css";

    /**
     * Property to control the selection of UI control for a selectOne control.
     * If a selectOne control has >= the number of values specified in this property,
     * it is considered a <b>long</b> list, and the UI control specified by
     * SELECTONE_UI_CONTROL_LONG_PROP is used. Otherwise, the value of SELECTONE_UI_CONTROL_SHORT_PROP
     * is used.
     */
    public static final String SELECTONE_LONG_LIST_SIZE_PROP =
            PROPERTY_PREFIX + "select1@longListSize";

    /**
     * Property to specify the selectMany UI control to be used when there are releatively few items
     * to choose from.
     */
    public static final String SELECTONE_UI_CONTROL_SHORT_PROP =
            PROPERTY_PREFIX + "select1@appearance/short";

    /**
     * Property to specify the selectMany UI control to be used when there are large numbers of items
     * to choose from.
     */
    public static final String SELECTONE_UI_CONTROL_LONG_PROP =
            PROPERTY_PREFIX + "select1@appearance/long";
    private static final String DEFAULT_SELECTONE_UI_CONTROL_SHORT_PROP =
            "full";
    private static final String DEFAULT_SELECTONE_UI_CONTROL_LONG_PROP =
            "minimal";

    /**
     * Property to control the selection of UI control for a selectMany control.
     * If a selectMany control has >= the number of values specified in this property,
     * it is considered a <b>long</b> list, and the UI control specified by
     * SELECTMANY_UI_CONTROL_LONG_PROP is used. Otherwise, the value of SELECTMANY_UI_CONTROL_SHORT_PROP
     * is used.
     */
    public static final String SELECTMANY_LONG_LIST_SIZE_PROP =
            PROPERTY_PREFIX + "select@longListSize";

    /**
     * Property to specify the selectMany UI control to be used when there are releatively few items
     * to choose from.
     */
    public static final String SELECTMANY_UI_CONTROL_SHORT_PROP =
            PROPERTY_PREFIX + "select@appearance/short";

    /**
     * Property to specify the selectMany UI control to be used when there are large numbers of items
     * to choose from.
     */
    public static final String SELECTMANY_UI_CONTROL_LONG_PROP =
            PROPERTY_PREFIX + "select@appearance/long";
    private static final String DEFAULT_SELECTMANY_UI_CONTROL_SHORT_PROP =
            "full";
    private static final String DEFAULT_SELECTMANY_UI_CONTROL_LONG_PROP =
            "compact";
    private static final String DEFAULT_LONG_LIST_MAX_SIZE = "6";

    /**
     * __UNDOCUMENTED__
     */
    protected Document _instanceDocument;

    /**
     * __UNDOCUMENTED__
     */
    protected String _action;

    /**
     * __UNDOCUMENTED__
     */
    protected String _submitMethod;

    /**
     * __UNDOCUMENTED__
     */
    protected String _base;

    /**
     * __UNDOCUMENTED__
     */
    protected WrapperElementsBuilder _wrapper = new XHTMLWrapperElementsBuilder();

    /**
     * generic counter -> replaced by an hashMap with:
     * keys: name of the elements
     * values: "Long" representing the counter for this element
     */
    private HashMap counter;
    private final Properties properties = new Properties();
    private String targetNamespace;

    private final Map namespacePrefixes = new HashMap();

    // typeTree
    // each entry is keyed by the type name
    // value is an ArrayList that contains the XSTypeDefinition's which
    // are compatible with the specific type. Compatible means that
    // can be used as a substituted type using xsi:type
    // In order for it to be compatible, it cannot be abstract, and
    // it must be derived by extension.
    // The ArrayList does not contain its own type + has the other types only once
    private final TreeMap<String, TreeSet<XSTypeDefinition>> typeTree = 
	new TreeMap<String, TreeSet<XSTypeDefinition>>();

    /**
     * Creates a new AbstractSchemaFormBuilder object.
     *
     * @param rootElementName    __UNDOCUMENTED__
     * @param instanceSource __UNDOCUMENTED__
     * @param action         __UNDOCUMENTED__
     * @param submitMethod   __UNDOCUMENTED__
     * @param wrapper        __UNDOCUMENTED__
     */
    public AbstractSchemaFormBuilder(final Document instanceDocument,
                                     final String action,
                                     final String submitMethod,
                                     final WrapperElementsBuilder wrapper,
                                     final String base) 
    {
        reset();
        this._instanceDocument = instanceDocument;

        this._action = action;
        this._base = base;

        //control if it is one of the SUBMIT_METHOD attributes?
        this._submitMethod = submitMethod;
        this._wrapper = wrapper;
    }

    /**
     * __UNDOCUMENTED__
     *
     * @return __UNDOCUMENTED__
     */
    public Properties getProperties() 
    {
        return properties;
    }

    /**
     * __UNDOCUMENTED__
     *
     * @param key   __UNDOCUMENTED__
     * @param value __UNDOCUMENTED__
     */
    public void setProperty(String key, String value) 
    {
        getProperties().setProperty(key, value);
    }

    /**
     * __UNDOCUMENTED__
     *
     * @param key __UNDOCUMENTED__
     * @return __UNDOCUMENTED__
     */
    public String getProperty(String key) 
    {
        return getProperties().getProperty(key);
    }

    /**
     * __UNDOCUMENTED__
     *
     * @param key          __UNDOCUMENTED__
     * @param defaultValue __UNDOCUMENTED__
     * @return __UNDOCUMENTED__
     */
    public String getProperty(String key, String defaultValue) 
    {
        return getProperties().getProperty(key, defaultValue);
    }

    /**
     * builds a form from a XML schema.
     *
     * @param inputURI the URI of the Schema to be used
     * @return __UNDOCUMENTED__
     * @throws FormBuilderException __UNDOCUMENTED__
     */
    public Document buildForm(final TemplateType tt) 
	throws FormBuilderException 
    {
	String rootElementName = tt.getName();
	final XSModel schema = this.loadSchema(tt);
	this.buildTypeTree(schema);
	
	//refCounter = 0;
	this.counter = new HashMap();
	
	final Document xForm = createFormTemplate(rootElementName);
	final Element envelopeElement = xForm.getDocumentElement();
	
	//Element formSection = (Element) envelopeElement.getElementsByTagNameNS(CHIBA_NS, "form").item(0);
	//Element formSection =(Element) envelopeElement.getElementsByTagName("body").item(0);
	//find form element: last element created
	final NodeList children = xForm.getDocumentElement().getChildNodes();
	
	final Element formSection = (Element)children.item(children.getLength() - 1);
	final Element modelSection = (Element)
	    envelopeElement.getElementsByTagNameNS(XFORMS_NS, "model").item(0);
	
	//add XMLSchema if we use schema types
	if (modelSection != null)
	{
	    final String schemaURI = this._base + tt.getSchemaURI();
	    LOGGER.debug("schema url is " + schemaURI);
	    modelSection.setAttributeNS(XFORMS_NS,
					SchemaFormBuilder.XFORMS_NS_PREFIX + "schema",
					schemaURI);
	}

	//xxx XSDNode node = findXSDNodeByName(rootElementTagName,schemaNode.getElementSet());
	
	//check if target namespace
	//no way to do this with XS API ? load DOM document ?
	//TODO: find a better way to find the targetNamespace
	final StringList targetNamespaces = schema.getNamespaces();
	if (targetNamespaces.getLength() != 0)
	{
	    // will return null if no target namespace was specified
	    this.targetNamespace = targetNamespaces.item(0);
	}
	LOGGER.debug("using targetNamespace " + this.targetNamespace);
	
	//if target namespace & we use the schema types: add it to form ns declarations
//	if (this.targetNamespace != null && this.targetNamespace.length() != 0)
//	    envelopeElement.setAttributeNS(XMLNS_NAMESPACE_URI,
//					   "xmlns:schema",
//					   this.targetNamespace);


	final Comment comment = 
	    xForm.createComment("This XForm was automatically generated by " + this.getClass().getName() + 
				" on " + (new Date()) + " from the '" + rootElementName + 
				"' element of the '" + this.targetNamespace + "' XML Schema.");
	xForm.insertBefore(comment, envelopeElement);
	
	//TODO: WARNING: in Xerces 2.6.1, parameters are switched !!! (name, namespace)
	//XSElementDeclaration rootElementDecl =schema.getElementDeclaration(this.targetNamespace, _rootElementName);
	XSElementDeclaration rootElementDecl = 
	    schema.getElementDeclaration(rootElementName, this.targetNamespace);
	
	if (rootElementDecl == null) 
	{
	    //Debug
	    rootElementDecl = schema.getElementDeclaration(this.targetNamespace,  
							   rootElementName);
	    if (rootElementDecl != null && LOGGER.isDebugEnabled())
		LOGGER.debug("getElementDeclaration: inversed parameters OK !!!");
	    
	    throw new FormBuilderException("Invalid root element tag name ["
					   + rootElementName
					   + ", targetNamespace="
					   + this.targetNamespace
					   + "]");
	}
	rootElementName = this.getElementName(rootElementDecl, xForm);
	final Element instanceElement = 
	    xForm.createElementNS(XFORMS_NS, 
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "instance");
	modelSection.appendChild(instanceElement);
	this.setXFormsId(instanceElement);

	final Element defaultInstanceRootElement = xForm.createElement(rootElementName);
	this.addNamespace(defaultInstanceRootElement, 
			  XMLSCHEMA_INSTANCE_NS_PREFIX, 
			  XMLSCHEMA_INSTANCE_NS);
	
	if (this._instanceDocument == null)
	    instanceElement.appendChild(defaultInstanceRootElement);
	else
	{
	    Element instanceDocumentElement = this._instanceDocument.getDocumentElement();
	    if (!instanceDocumentElement.getNodeName().equals(rootElementName))
		throw new IllegalArgumentException("instance document root tag name invalid.  " +
						   "expected " + rootElementName +
						   ", got " + instanceDocumentElement.getNodeName());
	    LOGGER.debug("importing rootElement from other document");
	    final Element importedInstanceRootElement = (Element)
		xForm.importNode(instanceDocumentElement, true);
	    //add XMLSchema instance NS
	    this.addNamespace(importedInstanceRootElement, 
			      XMLSCHEMA_INSTANCE_NS_PREFIX, 
			      XMLSCHEMA_INSTANCE_NS);
	    instanceElement.appendChild(importedInstanceRootElement);
	}

	Element formContentWrapper = this._wrapper.createGroupContentWrapper(formSection);
	this.addElement(xForm,
			modelSection,
			defaultInstanceRootElement,
			formContentWrapper,
			schema,
			rootElementDecl,
			rootElementDecl.getTypeDefinition(),
			"/" + getElementName(rootElementDecl, xForm));

	Element submitInfoElement = 
	    xForm.createElementNS(XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "submission");
	modelSection.appendChild(submitInfoElement);

	//submitInfoElement.setAttributeNS(XFORMS_NS,SchemaFormBuilder.XFORMS_NS_PREFIX+"id","save");
	String submissionId = this.setXFormsId(submitInfoElement);

	//action
	submitInfoElement.setAttributeNS(XFORMS_NS,
					 SchemaFormBuilder.XFORMS_NS_PREFIX + "action",
					 _action == null ? "" : this._base + _action);

	//method
	submitInfoElement.setAttributeNS(XFORMS_NS,
					 SchemaFormBuilder.XFORMS_NS_PREFIX + "method",
					 (_submitMethod != null && _submitMethod.length() != 0
					  ? _submitMethod
					  :  AbstractSchemaFormBuilder.SUBMIT_METHOD_POST));

	//Element submitButton = (Element) formSection.appendChild(xForm.createElementNS(XFORMS_NS,SchemaFormBuilder.XFORMS_NS_PREFIX+"submit"));
	Element submitButton =
	    xForm.createElementNS(XFORMS_NS, SchemaFormBuilder.XFORMS_NS_PREFIX + "submit");
	Element submitControlWrapper =
	    _wrapper.createControlsWrapper(submitButton);
	formContentWrapper.appendChild(submitControlWrapper);
	submitButton.setAttributeNS(XFORMS_NS,
				    SchemaFormBuilder.XFORMS_NS_PREFIX + "submission",
				    submissionId);
	this.setXFormsId(submitButton);

	Element submitButtonCaption = (Element)
	    submitButton.appendChild(xForm.createElementNS(XFORMS_NS,
							   SchemaFormBuilder.XFORMS_NS_PREFIX + "label"));
	submitButtonCaption.appendChild(xForm.createTextNode("Submit"));
	this.setXFormsId(submitButtonCaption);
	return xForm;
    }

    /**
     * This method is invoked after the form builder is finished creating and processing
     * a form control. Implementations may choose to use this method to add/inspect/modify
     * the controlElement prior to the builder moving onto the next control.
     *
     * @param controlElement The form control element that was created.
     * @param controlType    The XML Schema type for which <b>controlElement</b> was created.
     */
    public void endFormControl(Element controlElement,
                               XSTypeDefinition controlType,
                               Occurs occurs)
    {
    }

    /**
     * __UNDOCUMENTED__
     */
    public void reset() 
    {
        //refCounter = 0;
        counter = new HashMap();
        setProperty(CSS_STYLE_PROP, DEFAULT_CSS_STYLE_PROP);
        setProperty(SELECTMANY_LONG_LIST_SIZE_PROP, DEFAULT_LONG_LIST_MAX_SIZE);
        setProperty(SELECTMANY_UI_CONTROL_SHORT_PROP,
                DEFAULT_SELECTMANY_UI_CONTROL_SHORT_PROP);
        setProperty(SELECTMANY_UI_CONTROL_LONG_PROP,
                DEFAULT_SELECTMANY_UI_CONTROL_LONG_PROP);
        setProperty(SELECTONE_LONG_LIST_SIZE_PROP, DEFAULT_LONG_LIST_MAX_SIZE);
        setProperty(SELECTONE_UI_CONTROL_SHORT_PROP,
                DEFAULT_SELECTONE_UI_CONTROL_SHORT_PROP);
        setProperty(SELECTONE_UI_CONTROL_LONG_PROP,
                DEFAULT_SELECTONE_UI_CONTROL_LONG_PROP);
    }

    /**
     * Returns the most-specific built-in base type for the provided type.
     */
    protected short getBuiltInType(XSTypeDefinition type) {
        // type.getName() may be 'null' for anonymous types, so compare against
        // static string (see bug #1172541 on sf.net)
        if (("anyType").equals(type.getName())) {
            return XSConstants.ANYSIMPLETYPE_DT;
        } else {
            XSSimpleTypeDefinition simpleType = (XSSimpleTypeDefinition) type;

            //get built-in type
            //only working method found: getBuiltInKind, but it returns a short !
            //XSTypeDefinition builtIn = simpleType.getPrimitiveType();
            /*XSTypeDefinition builtIn = type.getBaseType();
            if (builtIn == null) {
                // always null for a ListType
                if (simpleType.getItemType() != null) //if not null it's a list
                    return getBuiltInType(simpleType.getItemType());
                else
                    return simpleType;
            }
            else if(LOGGER.isDebugEnabled())
                LOGGER.debug(" -> builtinType="+builtIn.getName());
            return builtIn;*/

            short result = simpleType.getBuiltInKind();
            if (result == XSConstants.LIST_DT) {
                result = getBuiltInType(simpleType.getItemType());
            }
            return result;
        }
    }

    /**
     * get the name of a datatype defined by its value in XSConstants
     * TODO: find an automatic way to do this !
     *
     * @param dt the short representating this datatype from XSConstants
     * @return the name of the datatype
     */
    public String getDataTypeName(short dt) {
        String name = "";
        switch (dt) {
            case XSConstants.ANYSIMPLETYPE_DT:
                name = "anyType";
                break;
            case XSConstants.ANYURI_DT:
                name = "anyURI";
                break;
            case XSConstants.BASE64BINARY_DT:
                name = "base64Binary";
                break;
            case XSConstants.BOOLEAN_DT:
                name = "boolean";
                break;
            case XSConstants.BYTE_DT:
                name = "byte";
                break;
            case XSConstants.DATE_DT:
                name = "date";
                break;
            case XSConstants.DATETIME_DT:
                name = "dateTime";
                break;
            case XSConstants.DECIMAL_DT:
                name = "decimal";
                break;
            case XSConstants.DOUBLE_DT:
                name = "double";
                break;
            case XSConstants.DURATION_DT:
                name = "duration";
                break;
            case XSConstants.ENTITY_DT:
                name = "ENTITY";
                break;
            case XSConstants.FLOAT_DT:
                name = "float";
                break;
            case XSConstants.GDAY_DT:
                name = "gDay";
                break;
            case XSConstants.GMONTH_DT:
                name = "gMonth";
                break;
            case XSConstants.GMONTHDAY_DT:
                name = "gMonthDay";
                break;
            case XSConstants.GYEAR_DT:
                name = "gYear";
                break;
            case XSConstants.GYEARMONTH_DT:
                name = "gYearMonth";
                break;
            case XSConstants.ID_DT:
                name = "ID";
                break;
            case XSConstants.IDREF_DT:
                name = "IDREF";
                break;
            case XSConstants.INT_DT:
                name = "int";
                break;
            case XSConstants.INTEGER_DT:
                name = "integer";
                break;
            case XSConstants.LANGUAGE_DT:
                name = "language";
                break;
            case XSConstants.LONG_DT:
                name = "long";
                break;
            case XSConstants.NAME_DT:
                name = "Name";
                break;
            case XSConstants.NCNAME_DT:
                name = "NCName";
                break;
            case XSConstants.NEGATIVEINTEGER_DT:
                name = "negativeInteger";
                break;
            case XSConstants.NMTOKEN_DT:
                name = "NMTOKEN";
                break;
            case XSConstants.NONNEGATIVEINTEGER_DT:
                name = "nonNegativeInteger";
                break;
            case XSConstants.NONPOSITIVEINTEGER_DT:
                name = "nonPositiveInteger";
                break;
            case XSConstants.NORMALIZEDSTRING_DT:
                name = "normalizedString";
                break;
            case XSConstants.NOTATION_DT:
                name = "NOTATION";
                break;
            case XSConstants.POSITIVEINTEGER_DT:
                name = "positiveInteger";
                break;
            case XSConstants.QNAME_DT:
                name = "QName";
                break;
            case XSConstants.SHORT_DT:
                name = "short";
                break;
            case XSConstants.STRING_DT:
                name = "string";
                break;
            case XSConstants.TIME_DT:
                name = "time";
                break;
            case XSConstants.TOKEN_DT:
                name = "TOKEN";
                break;
            case XSConstants.UNSIGNEDBYTE_DT:
                name = "unsignedByte";
                break;
            case XSConstants.UNSIGNEDINT_DT:
                name = "unsignedInt";
                break;
            case XSConstants.UNSIGNEDLONG_DT:
                name = "unsignedLong";
                break;
            case XSConstants.UNSIGNEDSHORT_DT:
                name = "unsignedShort";
                break;
        }
        return name;
    }

    protected String setXFormsId(final Element el)
    {
	return this.setXFormsId(el, null);
    }

    protected String setXFormsId(final Element el, String id) 
    {
        if (el.hasAttributeNS(SchemaFormBuilder.XFORMS_NS, "id"))
            el.removeAttributeNS(SchemaFormBuilder.XFORMS_NS, "id");
	if (id == null)
	{
	    long count = 0;
	    final String name = el.getLocalName();
	    final Long l = (Long) counter.get(name);
	    
	    if (l != null)
		count = l.longValue();
	    //increment the counter
	    counter.put(name, new Long(count + 1));
	    
	    id = name + "_" + count;
	}
        el.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
			  SchemaFormBuilder.XFORMS_NS_PREFIX + "id",
			  id);
        return id;
    }

    /**
     * method to set an Id to this element and to all XForms descendants of this element
     */
    private void resetXFormIds(Element newControl) 
    {
        if (newControl.getNamespaceURI() != null && 
	    newControl.getNamespaceURI().equals(XFORMS_NS))
            this.setXFormsId(newControl);

        //recursive call
        final NodeList children = newControl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE)
                this.resetXFormIds((Element) child);
        }
    }

    /**
     * __UNDOCUMENTED__
     *
     * @param xForm          __UNDOCUMENTED__
     * @param choicesElement __UNDOCUMENTED__
     * @param choiceValues   __UNDOCUMENTED__
     */
    protected void addChoicesForSelectControl(final Document xForm,
                                              final Element choicesElement,
                                              final List<String> choiceValues) {
        // sort the enums values and then add them as choices
        //
        // TODO: Should really put the default value (if any) at the top of the list.
        //
	//        List sortedList = choiceValues.subList(0, choiceValues.size());
	//        Collections.sort(sortedList);

	//        Iterator iterator = sortedList.iterator();

        for (String textValue : choiceValues) 
	{
            Element item = xForm.createElementNS(XFORMS_NS, 
						 SchemaFormBuilder.XFORMS_NS_PREFIX + "item");
            this.setXFormsId(item);
            choicesElement.appendChild(item);

            Element captionElement = xForm.createElementNS(XFORMS_NS, 
							   SchemaFormBuilder.XFORMS_NS_PREFIX + "label");
            this.setXFormsId(captionElement);
            item.appendChild(captionElement);
            captionElement.appendChild(xForm.createTextNode(createCaption(textValue)));

            Element value =
                    xForm.createElementNS(XFORMS_NS, SchemaFormBuilder.XFORMS_NS_PREFIX + "value");
            this.setXFormsId(value);
            item.appendChild(value);
            value.appendChild(xForm.createTextNode(textValue));
        }
    }

    //protected void addChoicesForSelectSwitchControl(Document xForm, Element choicesElement, Vector choiceValues, String bindIdPrefix) {
    protected Map<String, Element> addChoicesForSelectSwitchControl(final Document xForm,
								    final Element choicesElement,
								    final List<XSTypeDefinition> choiceValues) 
    {
        if (LOGGER.isDebugEnabled()) 
	{
            LOGGER.debug("addChoicesForSelectSwitchControl, values=");
            for (XSTypeDefinition type : choiceValues) 
	    {
                LOGGER.debug("  - " + type.getName());
            }
        }


        // sort the enums values and then add them as choices
        //
        // TODO: Should really put the default value (if any) at the top of the list.
        //
        /*List sortedList = choiceValues.subList(0, choiceValues.size());
        Collections.sort(sortedList);
        Iterator iterator = sortedList.iterator();*/
	// -> no, already sorted
	final Map<String, Element> result = new HashMap<String, Element>();
        for (XSTypeDefinition type : choiceValues)
	{
            String textValue = type.getName();
            //String textValue = (String) iterator.next();

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("addChoicesForSelectSwitchControl, processing " + textValue);

            Element item = xForm.createElementNS(XFORMS_NS, 
						 SchemaFormBuilder.XFORMS_NS_PREFIX + "item");
            this.setXFormsId(item);
            choicesElement.appendChild(item);

            Element captionElement = xForm.createElementNS(XFORMS_NS, 
							   SchemaFormBuilder.XFORMS_NS_PREFIX + "label");
            this.setXFormsId(captionElement);
            item.appendChild(captionElement);
            captionElement.appendChild(xForm.createTextNode(createCaption(textValue)));

	    Element value = xForm.createElementNS(XFORMS_NS, 
						  SchemaFormBuilder.XFORMS_NS_PREFIX + "value");
            this.setXFormsId(value);
            item.appendChild(value);
            value.appendChild(xForm.createTextNode(textValue));

	    /// action in the case

            Element action = xForm.createElementNS(XFORMS_NS,
						   SchemaFormBuilder.XFORMS_NS_PREFIX + "action");
            this.setXFormsId(action);
            item.appendChild(action);

            action.setAttributeNS(SchemaFormBuilder.XMLEVENTS_NS, 
				  SchemaFormBuilder.XMLEVENTS_NS_PREFIX + "event", 
				  "xforms-select");

            Element toggle = xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
						   SchemaFormBuilder.XFORMS_NS_PREFIX + "toggle");
            this.setXFormsId(toggle);

            //build the case element
            Element caseElement = xForm.createElementNS(XFORMS_NS, 
							SchemaFormBuilder.XFORMS_NS_PREFIX + "case");
            String case_id = this.setXFormsId(caseElement);
            result.put(textValue, caseElement);

            toggle.setAttributeNS(XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "case",
				  case_id);

            //toggle.setAttributeNS(XFORMS_NS,SchemaFormBuilder.XFORMS_NS_PREFIX + "case",bindIdPrefix + "_" + textValue +"_case");
            action.appendChild(toggle);
        }
	return result;
    }

    /**
     * __UNDOCUMENTED__
     *
     * @param xForm      __UNDOCUMENTED__
     * @param annotation __UNDOCUMENTED__
     * @return __UNDOCUMENTED__
     */
    protected Element addHintFromDocumentation(Document xForm,
                                               XSAnnotation annotation) {
        if (annotation == null)
	    return null;
	Element hintElement =
	    xForm.createElementNS(XFORMS_NS, SchemaFormBuilder.XFORMS_NS_PREFIX + "hint");
	this.setXFormsId(hintElement);
	
	Text hintText = (Text)
	    hintElement.appendChild(xForm.createTextNode(""));
	
	//write annotation to empty doc
	Document doc = DOMUtil.newDocument(true, false);
	annotation.writeAnnotation(doc, XSAnnotation.W3C_DOM_DOCUMENT);
	
	//get "annotation" element
	NodeList annots = doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema",
						     "annotation");
	if (annots.getLength() > 0) 
	{
	    Element annotEl = (Element) annots.item(0);
	    
	    //documentation
	    NodeList docos =
		annotEl.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema",
					       "documentation");
	    for (int j = 0; j < docos.getLength(); j++) 
	    {
		Element doco = (Element) docos.item(j);

		//get text value
		String text = DOMUtil.getTextNodeAsString(doco);
		hintText.appendData(text);
		
		if (j < docos.getLength() - 1) 
		    hintText.appendData(" ");
	    }
	    return hintElement;
	}
        return null;
    }

    public XSParticle findCorrespondingParticleInComplexType(final XSElementDeclaration elDecl) 
    {
        XSComplexTypeDefinition complexType = elDecl.getEnclosingCTDefinition();
        if (complexType == null)
	    return null;

	XSParticle particle = complexType.getParticle();
	XSTerm term = particle.getTerm();
	if (! (term instanceof XSModelGroup)) 
	    return null;

	XSModelGroup group = (XSModelGroup) term;
	XSObjectList particles = group.getParticles();
	if (particles == null)
	    return null;

	for (int i = 0; i < particles.getLength(); i++) 
	{
	    XSParticle part = (XSParticle) particles.item(i);
	    //test term
	    XSTerm thisTerm = part.getTerm();
	    if (thisTerm == elDecl)
		return part;
        }
        return null;
    }

    /**
     * finds the minOccurs and maxOccurs of an element declaration
     *
     * @return a table containing minOccurs and MaxOccurs
     */
    public Occurs getOccurance(XSElementDeclaration elDecl) 
    {
        //get occurance on encosing element declaration
        XSParticle particle =
                this.findCorrespondingParticleInComplexType(elDecl);
	Occurs result = new Occurs(particle);

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("getOccurance for " + elDecl.getName() + 
			 ", " + result);
        return result;
    }

    private void addAnyType(final Document xForm,
                            final Element modelSection,
                            final Element formSection,
			    final XSModel schema,
                            final XSTypeDefinition controlType,
                            final XSElementDeclaration owner,
                            final String pathToRoot) 
    {
        this.addSimpleType(xForm,
			   modelSection,
			   formSection,
			   schema,
			   controlType,
			   owner.getName(),
			   owner,
			   pathToRoot,
			   this.getOccurance(owner));
    }

    private void addAttributeSet(final Document xForm,
                                 final Element modelSection,
				 final Element defaultInstanceElement,
                                 final Element formSection,
				 final XSModel schema,
                                 final XSComplexTypeDefinition controlType,
                                 final XSElementDeclaration owner,
                                 final String pathToRoot,
                                 final boolean checkIfExtension) 
    {
        XSObjectList attrUses = controlType.getAttributeUses();

        if (attrUses == null)
	    return;
	for (int i = 0; i < attrUses.getLength(); i++) 
	{
	    final XSAttributeUse currentAttributeUse = (XSAttributeUse)attrUses.item(i);
	    final XSAttributeDeclaration currentAttribute =
		currentAttributeUse.getAttrDeclaration();
		
	    String attributeName = currentAttributeUse.getName();
	    if (attributeName == null || attributeName.length() == 0)
		attributeName = currentAttributeUse.getAttrDeclaration().getName();
	    
	    //test if extended !
	    if (checkIfExtension && 
		this.doesAttributeComeFromExtension(currentAttributeUse, controlType)) 
	    {
		if (LOGGER.isDebugEnabled()) 
		{
		    LOGGER.debug("This attribute comes from an extension: recopy form controls. \n Model section: ");
		    DOMUtil.prettyPrintDOM(modelSection);
		}
		
		//find the existing bind Id
		//(modelSection is the enclosing bind of the element)
		final NodeList binds = modelSection.getElementsByTagNameNS(XFORMS_NS, "bind");
		String bindId = null;
		for (int j = 0; j < binds.getLength() && bindId == null; j++) {
		    Element bind = (Element) binds.item(j);
		    String nodeset = bind.getAttributeNS(XFORMS_NS, "nodeset");
		    if (nodeset != null) 
		    {
			//remove "@" in nodeset
			String name = nodeset.substring(1);
			if (name.equals(attributeName))
			    bindId = bind.getAttributeNS(XFORMS_NS, "id");
		    }
		}
		
		//find the control
		Element control = null;
		if (bindId != null) 
		{
		    if (LOGGER.isDebugEnabled())
			LOGGER.debug("bindId found: " + bindId);
		    
		    JXPathContext context = JXPathContext.newContext(formSection.getOwnerDocument());
		    final Pointer pointer = 
			context.getPointer("//*[@" + SchemaFormBuilder.XFORMS_NS_PREFIX + 
					   "bind='" + bindId + "']");
		    if (pointer != null)
			control = (Element) pointer.getNode();
		}
		
		//copy it
		if (control == null)
		    LOGGER.warn("Corresponding control not found");
		else 
		{
		    Element newControl = (Element) control.cloneNode(true);
		    //set new Ids to XForm elements
		    this.resetXFormIds(newControl);
		    
		    formSection.appendChild(newControl);
		}
		
	    } 
	    else 
	    {
		defaultInstanceElement.setAttributeNS(this.targetNamespace,
						      // XXXarielb - i probably need the prefix here i.e. "alf:" + attributeName
						      attributeName,
						      (currentAttributeUse.getConstraintType() == XSConstants.VC_NONE
						       ? null
						       : currentAttributeUse.getConstraintValue()));
		final String newPathToRoot =
		    (pathToRoot == null || pathToRoot.length() == 0
		     ? "@" + currentAttribute.getName()
		     : (pathToRoot.endsWith("/")
			? pathToRoot + "@" + currentAttribute.getName()
			: pathToRoot + "/@" + currentAttribute.getName()));
		
		this.addSimpleType(xForm,
				   modelSection,
				   formSection,
				   schema,
				   currentAttribute.getTypeDefinition(),
				   currentAttributeUse,
				   newPathToRoot);
	    }
	}
    }

    private void addComplexType(final Document xForm,
                                final Element modelSection,
				final Element defaultInstanceElement,
                                final Element formSection,
				final XSModel schema,
                                final XSComplexTypeDefinition controlType,
                                final XSElementDeclaration owner,
                                final String pathToRoot,
				boolean relative,
                                final boolean checkIfExtension) {

        if (controlType == null) 
	{
	    if (LOGGER.isDebugEnabled())
		LOGGER.debug("addComplexType: control type is null for pathToRoot="
			     + pathToRoot);
	    return;
	}

	if (LOGGER.isDebugEnabled()) {
	    LOGGER.debug("addComplexType for " + controlType.getName());
	    if (owner != null)
		LOGGER.debug("	owner=" + owner.getName());
	}

	// add a group node and recurse
	//
	Element groupElement =
	    createGroup(xForm, modelSection, formSection, owner);
	Element groupWrapper = groupElement;
	
	if (groupElement != modelSection) 
	    groupWrapper = _wrapper.createGroupContentWrapper(groupElement);
	
	final Occurs o = this.getOccurance(owner);
	final Element repeatSection = this.addRepeatIfNecessary(xForm,
								modelSection,
								groupWrapper,
								controlType,
								o,
								pathToRoot);
	Element repeatContentWrapper = repeatSection;
	
	if (repeatSection != groupWrapper) 
	{ 
	    // we have a repeat
	    repeatContentWrapper =
		_wrapper.createGroupContentWrapper(repeatSection);
	    relative = true;
	}
	
	this.addComplexTypeChildren(xForm,
				    modelSection,
				    defaultInstanceElement,
				    repeatContentWrapper,
				    schema,
				    controlType,
				    owner,
				    pathToRoot,
				    relative,
				    checkIfExtension);
	
	Element realModel = modelSection;
	if (relative) 
	{
	    //modelSection: find the last element put in the modelSection = bind
	    realModel = DOMUtil.getLastChildElement(modelSection);
	}
	
	this.endFormGroup(groupElement, controlType, o, realModel);
    }

    private void addComplexTypeChildren(final Document xForm,
					Element modelSection,
					final Element defaultInstanceElement,
                                        final Element formSection,
					final XSModel schema,
                                        final XSComplexTypeDefinition controlType,
                                        final XSElementDeclaration owner,
					String pathToRoot,
                                        final boolean relative,
                                        final boolean checkIfExtension) {

        if (controlType == null)
	    return;

        if (LOGGER.isDebugEnabled()) 
	{
            LOGGER.debug("addComplexTypeChildren for " + controlType.getName());
            if (owner != null)
                LOGGER.debug("	owner=" + owner.getName());
        }

        if (controlType.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_MIXED || 
	    (controlType.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE && 
	     controlType.getAttributeUses() != null && 
	     controlType.getAttributeUses().getLength() > 0)) 
	{
            XSTypeDefinition base = controlType.getBaseType();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("	Control type is mixed . base type=" + base.getName());

            if (base != null && base != controlType) 
	    {
                if (base.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) 
		{
                    this.addSimpleType(xForm,
				       modelSection,
				       formSection,
				       schema,
				       (XSSimpleTypeDefinition) base,
				       owner,
				       pathToRoot);
                }
		else
                    LOGGER.warn("addComplexTypeChildren for mixed type with basic type complex !");
            }
        } 
	else if (LOGGER.isDebugEnabled())
            LOGGER.debug("	Content type = " + controlType.getContentType());


        // check for compatible subtypes
        // of controlType.
        // add a type switch if there are any
        // compatible sub-types (i.e. anything
        // that derives from controlType)
        // add child elements
        if (relative) 
	{
            pathToRoot = "";

            //modelSection: find the last element put in the modelSection = bind
            modelSection = DOMUtil.getLastChildElement(modelSection);
        }

        //attributes
        this.addAttributeSet(xForm,
			     modelSection,
			     defaultInstanceElement,
			     formSection,
			     schema,
			     controlType,
			     owner,
			     pathToRoot,
			     checkIfExtension);

        //process group
        final XSParticle particle = controlType.getParticle();
        if (particle != null) 
	{
            final XSTerm term = particle.getTerm();
            if (! (term instanceof XSModelGroup)) 
	    {
		if (LOGGER.isDebugEnabled())
		    LOGGER.debug("	Particle of " + controlType.getName() + 
				 " is not a group: " + term.getClass().getName());
	    }
	    else
	    {
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("	Particle of " + controlType.getName() + 
				 " is a group --->");

                XSModelGroup group = (XSModelGroup) term;
                //call addGroup on this group
                this.addGroup(xForm,
			      modelSection,
			      defaultInstanceElement,
			      formSection,
			      schema,
			      group,
			      controlType,
			      owner,
			      pathToRoot,
			      new Occurs(particle),
			      checkIfExtension);

            }
        }

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("--->end of addComplexTypeChildren for " + controlType.getName());
    }

    /**
     * add an element to the XForms document: the bind + the control
     * (only the control if "withBind" is false)
     */
    private void addElement(final Document xForm,
                            final Element modelSection,
			    final Element defaultInstanceElement,
                            final Element formSection,
			    final XSModel schema,
                            final XSElementDeclaration elementDecl,
			    XSTypeDefinition controlType,
                            final String pathToRoot) {

        if (controlType == null) 
	{
            // TODO!!! Figure out why this happens... for now just warn...
            // seems to happen when there is an element of type IDREFS
            LOGGER.warn("WARNING!!! controlType is null for " + elementDecl + 
			", " + elementDecl.getName());
            return;
        }

        switch (controlType.getTypeCategory()) 
	{
	case XSTypeDefinition.SIMPLE_TYPE:
	{
	    this.addSimpleType(xForm,
			       modelSection,
			       formSection,
			       schema,
			       (XSSimpleTypeDefinition) controlType,
			       elementDecl,
			       pathToRoot);
	    break;
	}
	case XSTypeDefinition.COMPLEX_TYPE:
	{
	    final String typeName = controlType.getName();	    
	    if ("anyType".equals(typeName)) 
	    {
		this.addAnyType(xForm,
				modelSection,
				formSection,
				schema,
				(XSComplexTypeDefinition) controlType,
				elementDecl,
				pathToRoot);
		break;
	    }
		
	    // find the types which are compatible(derived from) the parent type.
	    //
	    // This is used if we encounter a XML Schema that permits the xsi:type
	    // attribute to specify subtypes for the element.
	    //
	    // For example, the <address> element may be typed to permit any of
	    // the following scenarios:
	    // <address xsi:type="USAddress">
	    // </address>
	    // <address xsi:type="CanadianAddress">
	    // </address>
	    // <address xsi:type="InternationalAddress">
	    // </address>
	    //
	    // What we want to do is generate an XForm' switch element with cases
	    // representing any valid non-abstract subtype.
	    //
	    // <xforms:select1 xforms:bind="xsi_type_13"
	    //		  <xforms:label>Address</xforms:label>
	    //        <xforms:choices>
	    //                <xforms:item>
	    //                        <xforms:label>US Address Type</xforms:label>
	    //                        <xforms:value>USAddressType</xforms:value>
	    //                        <xforms:action ev:event="xforms-select">
	    //                                <xforms:toggle xforms:case="USAddressType-case"/>
	    //                        </xforms:action>
	    //                </xforms:item>
	    //                <xforms:item>
	    //                        <xforms:label>Canadian Address Type</xforms:label>
	    //                        <xforms:value>CanadianAddressType</xforms:value>
	    //                        <xforms:action ev:event="xforms-select">
	    //                                <xforms:toggle xforms:case="CanadianAddressType-case"/>
	    //                        </xforms:action>
	    //                </xforms:item>
	    //                <xforms:item>
	    //                        <xforms:label>International Address Type</xforms:label>
	    //                        <xforms:value>InternationalAddressType</xforms:value>
	    //                        <xforms:action ev:event="xforms-select">
	    //                                <xforms:toggle xforms:case="InternationalAddressType-case"/>
	    //                        </xforms:action>
	    //                </xforms:item>
	    //
	    //          </xforms:choices>
	    // <xforms:select1>
	    // <xforms:trigger>
	    //	<xforms:label>validate Address type</xforms:label>
	    //	<xforms:action>
	    //		<xforms:dispatch id="dispatcher" xforms:name="xforms-activate" xforms:target="select1_0"/>
	    //	</xforms:action>
	    //</xforms:trigger>
	    //
	    // <xforms:switch id="address_xsi_type_switch">
	    //      <xforms:case id="USAddressType-case" selected="false">
	    //          <!-- US Address Type sub-elements here-->
	    //      </xforms:case>
	    //      <xforms:case id="CanadianAddressType-case" selected="false">
	    //          <!-- US Address Type sub-elements here-->
	    //      </xforms:case>
	    //      ...
	    // </xforms:switch>
	    //
	    //   + change bindings to add:
	    //	- a bind for the "@xsi:type" attribute
	    //	- for each possible element that can be added through the use of an inheritance, add a "relevant" attribute:
	    //	ex: xforms:relevant="../@xsi:type='USAddress'"
	    
	    // look for compatible types
	    //

	    boolean relative = true;
	    if (typeName != null) 
	    {
		final TreeSet<XSTypeDefinition> compatibleTypes = 
		    this.typeTree.get(controlType.getName());
		//TreeSet compatibleTypes = (TreeSet) typeTree.get(controlType);
		
		if (compatibleTypes == null) 
		{
		    if (LOGGER.isDebugEnabled())
			LOGGER.debug("No compatible type found for " + typeName);
		}
		else
		{
		    relative = false;
		    
		    if (true || LOGGER.isDebugEnabled()) 
		    {
			LOGGER.debug("compatible types for " + typeName + ":");
			for (XSTypeDefinition compType : compatibleTypes) 
			{
			    LOGGER.debug("          compatible type name=" + compType.getName());
			}
		    }
		    
		    Element control = xForm.createElementNS(XFORMS_NS,
							    SchemaFormBuilder.XFORMS_NS_PREFIX + 
							    "select1");
		    String select1_id = this.setXFormsId(control);
		    
		    Element choices = xForm.createElementNS(XFORMS_NS,
							    SchemaFormBuilder.XFORMS_NS_PREFIX + "choices");
		    this.setXFormsId(choices);
		    
		    //get possible values
		    List<XSTypeDefinition> enumValues = new LinkedList<XSTypeDefinition>();
		    //add the type (if not abstract)
		    if (!((XSComplexTypeDefinition) controlType).getAbstract())
			enumValues.add(controlType);
		    
		    //add compatible types
		    enumValues.addAll(compatibleTypes);
		    
		    if (enumValues.size() == 1) 
		    {
			// only one compatible type, set the controlType value
			// and fall through
			//
			//controlType = schema.getTypeDefinition((String)enumValues.get(0),
			//				       this.targetNamespace);
			controlType = enumValues.get(0);
		    }
		    else if (enumValues.size() > 1) 
		    {
			String caption = createCaption(elementDecl.getName() + " Type");
			Element controlCaption = 
			    xForm.createElementNS(XFORMS_NS,
						  SchemaFormBuilder.XFORMS_NS_PREFIX + "label");
			control.appendChild(controlCaption);
			this.setXFormsId(controlCaption);
			controlCaption.appendChild(xForm.createTextNode(caption));
			
			// multiple compatible types for this element exist
			// in the schema - allow the user to choose from
			// between compatible non-abstract types
			Element bindElement = xForm.createElementNS(XFORMS_NS,
								    SchemaFormBuilder.XFORMS_NS_PREFIX + "bind");
			String bindId = this.setXFormsId(bindElement);
			
			bindElement.setAttributeNS(XFORMS_NS,
						   SchemaFormBuilder.XFORMS_NS_PREFIX + "nodeset",
						   pathToRoot + "/@xsi:type");
			
			modelSection.appendChild(bindElement);
			control.setAttributeNS(XFORMS_NS,
					       SchemaFormBuilder.XFORMS_NS_PREFIX + "bind",
					       bindId);
			
			//add the "element" bind, in addition
			Element bindElement2 = xForm.createElementNS(XFORMS_NS,
								     SchemaFormBuilder.XFORMS_NS_PREFIX + "bind");
			String bindId2 = this.setXFormsId(bindElement2);
			bindElement2.setAttributeNS(XFORMS_NS,
						    SchemaFormBuilder.XFORMS_NS_PREFIX + "nodeset",
						    pathToRoot);
			
			modelSection.appendChild(bindElement2);
			
			control.setAttributeNS(XFORMS_NS,
					       SchemaFormBuilder.XFORMS_NS_PREFIX + "appearance",
					       (enumValues.size() < Long.parseLong(getProperty(SELECTONE_LONG_LIST_SIZE_PROP))
						? getProperty(SELECTONE_UI_CONTROL_SHORT_PROP)
						: getProperty(SELECTONE_UI_CONTROL_LONG_PROP)));

			if (enumValues.size() >= Long.parseLong(getProperty(SELECTONE_LONG_LIST_SIZE_PROP)))
			{
			    // add the "Please select..." instruction item for the combobox
			    // and set the isValid attribute on the bind element to check for the "Please select..."
			    // item to indicate that is not a valid value
			    //
			    String pleaseSelect = "[Select1 " + caption + "]";
			    Element item = xForm.createElementNS(XFORMS_NS,
								 SchemaFormBuilder.XFORMS_NS_PREFIX + "item");
			    this.setXFormsId(item);
			    choices.appendChild(item);
			    
			    Element captionElement = xForm.createElementNS(XFORMS_NS,
									   SchemaFormBuilder.XFORMS_NS_PREFIX + "label");
			    this.setXFormsId(captionElement);
			    item.appendChild(captionElement);
			    captionElement.appendChild(xForm.createTextNode(pleaseSelect));
			    
			    Element value = xForm.createElementNS(XFORMS_NS,
								  SchemaFormBuilder.XFORMS_NS_PREFIX + "value");
			    this.setXFormsId(value);
			    item.appendChild(value);
			    value.appendChild(xForm.createTextNode(pleaseSelect));
			    
			    // not(purchaseOrder/state = '[Choose State]')
			    //String isValidExpr = "not(" + bindElement.getAttributeNS(XFORMS_NS, "nodeset") + " = '" + pleaseSelect + "')";
			    // ->no, not(. = '[Choose State]')
			    String isValidExpr = "not( . = '" + pleaseSelect + "')";
			    
			    //check if there was a constraint
			    String constraint = bindElement.getAttributeNS(XFORMS_NS, "constraint");
			    
			    constraint = (constraint != null && constraint.length() != 0
					  ? constraint + " && " + isValidExpr
					  : isValidExpr);
			    
			    bindElement.setAttributeNS(XFORMS_NS,
						       SchemaFormBuilder.XFORMS_NS_PREFIX
						       + "constraint",
						       constraint);
			}

			Element choicesControlWrapper = _wrapper.createControlsWrapper(choices);
			control.appendChild(choicesControlWrapper);
			
			Element controlWrapper = _wrapper.createControlsWrapper(control);
			formSection.appendChild(controlWrapper);
			
			/////////////////                                      ///////////////
			// add content to select1
			final Map<String, Element> caseTypes = 
			    this.addChoicesForSelectSwitchControl(xForm, choices, enumValues);
			
			/////////////////
			//add a trigger for this control (is there a way to not need it ?)
			Element trigger = xForm.createElementNS(XFORMS_NS,
								SchemaFormBuilder.XFORMS_NS_PREFIX + "trigger");
			formSection.appendChild(trigger);
			this.setXFormsId(trigger);
			Element label_trigger = xForm.createElementNS(XFORMS_NS,
								      SchemaFormBuilder.XFORMS_NS_PREFIX + "label");
			this.setXFormsId(label_trigger);
			trigger.appendChild(label_trigger);
			String trigger_caption = createCaption("validate choice");
			label_trigger.appendChild(xForm.createTextNode(trigger_caption));
			Element action_trigger = xForm.createElementNS(XFORMS_NS,
								       SchemaFormBuilder.XFORMS_NS_PREFIX + "action");
			this.setXFormsId(action_trigger);
			trigger.appendChild(action_trigger);
			Element dispatch_trigger = xForm.createElementNS(XFORMS_NS,
									 SchemaFormBuilder.XFORMS_NS_PREFIX + "dispatch");
			this.setXFormsId(dispatch_trigger);
			action_trigger.appendChild(dispatch_trigger);
			dispatch_trigger.setAttributeNS(XFORMS_NS,
							SchemaFormBuilder.XFORMS_NS_PREFIX + "name",
							"DOMActivate");
			dispatch_trigger.setAttributeNS(XFORMS_NS,
							SchemaFormBuilder.XFORMS_NS_PREFIX + "target",
							select1_id);
			
			/////////////////
			//add switch
			Element switchElement = xForm.createElementNS(XFORMS_NS,
								      SchemaFormBuilder.XFORMS_NS_PREFIX + "switch");
			this.setXFormsId(switchElement);
			
			Element switchControlWrapper =
			    _wrapper.createControlsWrapper(switchElement);
			formSection.appendChild(switchControlWrapper);
			//formSection.appendChild(switchElement);
			
			/////////////// add this type //////////////
			Element firstCaseElement = caseTypes.get(controlType.getName());
			switchElement.appendChild(firstCaseElement);
			this.addComplexType(xForm,
					    modelSection,
					    defaultInstanceElement,
					    firstCaseElement,
					    schema,
					    (XSComplexTypeDefinition)controlType,
					    elementDecl,
					    pathToRoot,
					    true,
					    false);
			
			/////////////// add sub types //////////////
			// add each compatible type within
			// a case statement
			for (XSTypeDefinition type : compatibleTypes) 
			{
			    /*String compatibleTypeName = (String) it.next();
			    //WARNING: order of parameters inversed from the doc for 2.6.0 !!!
			    XSTypeDefinition type =getSchema().getTypeDefinition(
			    compatibleTypeName,
			    targetNamespace);*/
			    String compatibleTypeName = type.getName();
			    
			    if (LOGGER.isDebugEnabled()) 
				LOGGER.debug(type == null
					     ? (">>>addElement: compatible type is null!! type=" + 
						compatibleTypeName + ", targetNamespace=" + this.targetNamespace)
					     : ("   >>>addElement: adding compatible type " + type.getName()));
			    
			    if (type != null && 
				type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) 
			    {
				
				//Element caseElement = (Element) xForm.createElementNS(XFORMS_NS,SchemaFormBuilder.XFORMS_NS_PREFIX + "case");
				//caseElement.setAttributeNS(XFORMS_NS,SchemaFormBuilder.XFORMS_NS_PREFIX + "id",bindId + "_" + type.getName() +"_case");
				//String case_id=this.setXFormsId(caseElement);
				Element caseElement = caseTypes.get(type.getName());
				switchElement.appendChild(caseElement);
				
				this.addComplexType(xForm,
						    modelSection,
						    defaultInstanceElement,
						    caseElement,
						    schema,
						    (XSComplexTypeDefinition) type,
						    elementDecl,
						    pathToRoot,
						    true,
						    true);
				
				//////
				// modify bind to add a "relevant" attribute that checks the value of @xsi:type
				//
				if (LOGGER.isDebugEnabled())
				    DOMUtil.prettyPrintDOM(bindElement2);
				NodeList binds = bindElement2.getElementsByTagNameNS(XFORMS_NS, "bind");
				Element thisBind = null;
				for (int i = 0; i < binds.getLength() && thisBind == null; i++) 
				{
				    Element subBind = (Element) binds.item(i);
				    String name = subBind.getAttributeNS(XFORMS_NS, "nodeset");
				    
				    if (LOGGER.isDebugEnabled())
					LOGGER.debug("Testing sub-bind with nodeset " + name);
				    
				    if (this.isElementDeclaredIn(name, (XSComplexTypeDefinition) type, false) || 
					this.isAttributeDeclaredIn(name, (XSComplexTypeDefinition) type, false))
				    {
					if (LOGGER.isDebugEnabled())
					    LOGGER.debug("Element/Attribute " + name + " declared in type " + type.getName() + ": adding relevant attribute");

					//test sub types of this type
					final TreeSet<XSTypeDefinition> subCompatibleTypes = this.typeTree.get(type.getName());
					//TreeSet subCompatibleTypes = (TreeSet) typeTree.get(type);
					
					String newRelevant = null;
					if (subCompatibleTypes == null || subCompatibleTypes.isEmpty()) 
					{
					    //just add ../@xsi:type='type'
					    newRelevant = "../@xsi:type='" + type.getName() + "'";
					}
					else 
					{
					    //add ../@xsi:type='type' or ../@xsi:type='otherType' or ...
					    newRelevant = "../@xsi:type='" + type.getName() + "'";
					    for (XSTypeDefinition otherType : subCompatibleTypes) 
					    {
						String otherTypeName = otherType.getName();
						newRelevant = newRelevant + " or ../@xsi:type='" + otherTypeName + "'";
					    }
					}

					//change relevant attribute
					String relevant = subBind.getAttributeNS(XFORMS_NS, "relevant");
					if (relevant != null && relevant.length() != 0) 
					    newRelevant = ("(" + relevant + 
							   ") and " + newRelevant);
					if (newRelevant != null && newRelevant.length() != 0)
					    subBind.setAttributeNS(XFORMS_NS, 
								   SchemaFormBuilder.XFORMS_NS_PREFIX + "relevant", 
								   newRelevant);
				    }
				}
			    }
			}

			/*if (LOGGER.isDebugEnabled()) {
			  LOGGER.debug(
			  "###addElement for derived type: bind created:");
			  DOMUtil.prettyPrintDOM(bindElement2);
			  }*/

			// we're done
			//
			break;

		    } 
		}
		//name not null but no compatibleType?
		relative = true;
	    }

            if (!relative) //create the bind in case it is a repeat
	    {
		if (LOGGER.isDebugEnabled()) 
		    LOGGER.debug("addElement: bind is not relative for "
				 + elementDecl.getName());
	    }
	    else
	    {
		if (LOGGER.isDebugEnabled())
		    LOGGER.debug(">>>Adding empty bind for " + typeName);

		// create the <xforms:bind> element and add it to the model.
		Element bindElement =
		    xForm.createElementNS(XFORMS_NS,
					  SchemaFormBuilder.XFORMS_NS_PREFIX + "bind");
		String bindId = this.setXFormsId(bindElement);
		bindElement.setAttributeNS(XFORMS_NS,
					   SchemaFormBuilder.XFORMS_NS_PREFIX + "nodeset",
					   pathToRoot);

		modelSection.appendChild(bindElement);
	    } 

	    //addComplexType(xForm,modelSection, formSection,(ComplexType)controlType,elementDecl,pathToRoot, relative);
	    this.addComplexType(xForm,
				modelSection,
				defaultInstanceElement,
				formSection,
				schema,
				(XSComplexTypeDefinition)controlType,
				elementDecl,
				pathToRoot,
				true,
				false);
			
	    break;
	}
	
	default : // TODO: add wildcard support
	    LOGGER.warn("\nWARNING!!! - Unsupported type [" + elementDecl.getType() +
			"] for node [" + controlType.getName() + "]");
	}
    }
    
    /**
     * check that the element defined by this name is declared directly in the type
     */
    private boolean isElementDeclaredIn(String name, 
					XSComplexTypeDefinition type, 
					boolean recursive) 
    {
        boolean found = false;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("isElement " + name + " declared in " + type.getName());

        //test if extension + declared in parent + not recursive -> NOK
        if (!recursive && type.getDerivationMethod() == XSConstants.DERIVATION_EXTENSION) 
	{
            XSComplexTypeDefinition parent = (XSComplexTypeDefinition) type.getBaseType();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("testing if it is not on parent " + parent.getName());
            if (this.isElementDeclaredIn(name, parent, true))
                return false;
        }

        XSParticle particle = type.getParticle();
        if (particle != null) 
	{
            XSTerm term = particle.getTerm();
            if (term instanceof XSModelGroup) 
	    {
                XSModelGroup group = (XSModelGroup) term;
                found = this.isElementDeclaredIn(name, group);
            }
        }

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("isElement " + name + 
			 " declared in " + type.getName() + ": " + found);

        return found;
    }

    /**
     * private recursive method called by isElementDeclaredIn(String name, XSComplexTypeDefinition type)
     */
    private boolean isElementDeclaredIn(String name, XSModelGroup group) 
    {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("isElement " + name + " declared in group " + group.getName());

        boolean found = false;
        XSObjectList particles = group.getParticles();
        for (int i = 0; i < particles.getLength(); i++) 
	{
            XSParticle subPart = (XSParticle) particles.item(i);
            XSTerm subTerm = subPart.getTerm();
            if (subTerm instanceof XSElementDeclaration) 
	    {
                XSElementDeclaration elDecl = (XSElementDeclaration) subTerm;
                if (name.equals(elDecl.getName()))
                    found = true;
            } 
	    else if (subTerm instanceof XSModelGroup)
	    {
                found = this.isElementDeclaredIn(name, (XSModelGroup) subTerm);
            }
        }

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("isElement " + name + " declared in group " + group.getName() + ": " + found);
        return found;
    }

    private boolean doesElementComeFromExtension(XSElementDeclaration element, XSComplexTypeDefinition controlType) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("doesElementComeFromExtension for " + element.getName() + " and controlType=" + controlType.getName());
        boolean comesFromExtension = false;
        if (controlType.getDerivationMethod() == XSConstants.DERIVATION_EXTENSION) {
            XSTypeDefinition baseType = controlType.getBaseType();
            if (baseType.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
                XSComplexTypeDefinition complexType = (XSComplexTypeDefinition) baseType;
                if (this.isElementDeclaredIn(element.getName(), complexType, true)) {
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("doesElementComeFromExtension: yes");
                    comesFromExtension = true;
                } else { //recursive call
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("doesElementComeFromExtension: recursive call on previous level");
                    comesFromExtension = this.doesElementComeFromExtension(element, complexType);
                }
            }
        }
        return comesFromExtension;
    }

    /**
     * check that the element defined by this name is declared directly in the type
     */
    private boolean isAttributeDeclaredIn(XSAttributeUse attr, XSComplexTypeDefinition type, boolean recursive) {
        boolean found = false;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("is Attribute " + attr.getAttrDeclaration().getName() + " declared in " + type.getName());

//check on parent if not recursive
        if (!recursive && type.getDerivationMethod() == XSConstants.DERIVATION_EXTENSION) {
            XSComplexTypeDefinition parent = (XSComplexTypeDefinition) type.getBaseType();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("testing if it is not on parent " + parent.getName());
            if (this.isAttributeDeclaredIn(attr, parent, true))
                return false;
        }

//check on this type  (also checks recursively)
        XSObjectList attrs = type.getAttributeUses();
        int nb = attrs.getLength();
        int i = 0;
        while (i < nb && !found) {
            XSAttributeUse anAttr = (XSAttributeUse) attrs.item(i);
            if (anAttr == attr)
                found = true;
            i++;
        }

//recursive call
/*if(!found && recursive &&
                type.getDerivationMethod()==XSConstants.DERIVATION_EXTENSION){
                    XSComplexTypeDefinition base=(XSComplexTypeDefinition) type.getBaseType();
                    if(base!=null && base!=type)
                        found = this.isAttributeDeclaredIn(attr, base, true);
                }*/

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("is Attribute " + attr.getName() + " declared in " + type.getName() + ": " + found);

        return found;
    }

    /**
     * check that the element defined by this name is declared directly in the type
     * -> idem with string
     */
    private boolean isAttributeDeclaredIn(String attrName, XSComplexTypeDefinition type, boolean recursive) {
        boolean found = false;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("is Attribute " + attrName + " declared in " + type.getName());

        if (attrName.startsWith("@"))
            attrName = attrName.substring(1);

//check on parent if not recursive
        if (!recursive && type.getDerivationMethod() == XSConstants.DERIVATION_EXTENSION) {
            XSComplexTypeDefinition parent = (XSComplexTypeDefinition) type.getBaseType();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("testing if it is not on parent " + parent.getName());
            if (this.isAttributeDeclaredIn(attrName, parent, true))
                return false;
        }

//check on this type (also checks recursively)
        XSObjectList attrs = type.getAttributeUses();
        int nb = attrs.getLength();
        int i = 0;
        while (i < nb && !found) {
            XSAttributeUse anAttr = (XSAttributeUse) attrs.item(i);
            if (anAttr != null) {
                String name = anAttr.getName();
                if (name == null || name.length() == 0)
                    name = anAttr.getAttrDeclaration().getName();
                if (attrName.equals(name))
                    found = true;
            }
            i++;
        }

//recursive call -> no need
/*if(!found && recursive &&
                type.getDerivationMethod()==XSConstants.DERIVATION_EXTENSION){
                    XSComplexTypeDefinition base=(XSComplexTypeDefinition) type.getBaseType();
                    if(base!=null && base!=type)
                        found = this.isAttributeDeclaredIn(attrName, base, true);
                }*/

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("is Attribute " + attrName + " declared in " + type.getName() + ": " + found);

        return found;
    }

    private boolean doesAttributeComeFromExtension(XSAttributeUse attr, XSComplexTypeDefinition controlType) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("doesAttributeComeFromExtension for " + attr.getAttrDeclaration().getName() + " and controlType=" + controlType.getName());
        boolean comesFromExtension = false;
        if (controlType.getDerivationMethod() == XSConstants.DERIVATION_EXTENSION) {
            XSTypeDefinition baseType = controlType.getBaseType();
            if (baseType.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
                XSComplexTypeDefinition complexType = (XSComplexTypeDefinition) baseType;
                if (this.isAttributeDeclaredIn(attr, complexType, true)) {
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("doesAttributeComeFromExtension: yes");
                    comesFromExtension = true;
                } else { //recursive call
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("doesAttributeComeFromExtension: recursive call on previous level");
                    comesFromExtension = this.doesAttributeComeFromExtension(attr, complexType);
                }
            }
        }
        return comesFromExtension;
    }

    /**
     * checkIfExtension: if false, addElement is called wether it is an extension or not
     * if true, if it is an extension, element is recopied (and no additional bind)
     */
    private void addGroup(final Document xForm,
                          final Element modelSection,
			  final Element defaultInstanceElement,
                          final Element formSection,
			  final XSModel schema,
                          final XSModelGroup group,
                          final XSComplexTypeDefinition controlType,
                          final XSElementDeclaration owner,
                          final String pathToRoot,
                          final Occurs o,
                          final boolean checkIfExtension) 
    {
        if (group == null) 
	    return;

	final Element repeatSection = 
	    this.addRepeatIfNecessary(xForm,
				      modelSection,
				      formSection,
				      owner.getTypeDefinition(),
				      o,
				      pathToRoot);
	Element repeatContentWrapper = repeatSection;
	
	if (repeatSection != formSection) 
	{
	    //selector -> no more needed?
	    //this.addSelector(xForm, repeatSection);
	    //group wrapper
	    repeatContentWrapper =
		_wrapper.createGroupContentWrapper(repeatSection);
	}

	if (LOGGER.isDebugEnabled())
	    LOGGER.debug("addGroup from owner=" + owner.getName() + 
			 " and controlType=" + controlType.getName());

	final XSObjectList particles = group.getParticles();
	for (int counter = 0; counter < particles.getLength(); counter++) 
	{
	    final XSParticle currentNode = (XSParticle) particles.item(counter);
	    XSTerm term = currentNode.getTerm();

	    if (LOGGER.isDebugEnabled())
		LOGGER.debug("	: next term = " + term.getName());

	    final Occurs childOccurs = new Occurs(currentNode);

	    if (term instanceof XSModelGroup) 
	    {

		if (LOGGER.isDebugEnabled())
		    LOGGER.debug("	term is a group");

		this.addGroup(xForm,
			      modelSection,
			      defaultInstanceElement,
			      repeatContentWrapper,
			      schema,
			      ((XSModelGroup) term),
			      controlType,
			      owner,
			      pathToRoot,
			      childOccurs,
			      checkIfExtension);
	    } 
	    else if (term instanceof XSElementDeclaration) 
	    {
		XSElementDeclaration element = (XSElementDeclaration) term;

		if (LOGGER.isDebugEnabled())
		    LOGGER.debug("	term is an element declaration: "
				 + term.getName());

		//special case for types already added because used in an extension
		//do not add it when it comes from an extension !!!
		//-> make a copy from the existing form control
		if (checkIfExtension && 
		    this.doesElementComeFromExtension(element, controlType)) 
		{
		    if (LOGGER.isDebugEnabled()) 
		    {
			LOGGER.debug("This element comes from an extension: recopy form controls.\n Model Section=");
			DOMUtil.prettyPrintDOM(modelSection);
		    }

		    //find the existing bind Id
		    //(modelSection is the enclosing bind of the element)
		    NodeList binds = modelSection.getElementsByTagNameNS(XFORMS_NS, "bind");
		    String bindId = null;
		    for (int i = 0; i < binds.getLength() && bindId == null; i++) 
		    {
			Element bind = (Element) binds.item(i);
			String nodeset = bind.getAttributeNS(XFORMS_NS, "nodeset");
			if (nodeset != null && nodeset.equals(element.getName()))
			    bindId = bind.getAttributeNS(XFORMS_NS, "id");
		    }

		    //find the control
		    Element control = null;
		    if (bindId != null) 
		    {
			if (LOGGER.isDebugEnabled())
			    LOGGER.debug("bindId found: " + bindId);

			final JXPathContext context = 
			    JXPathContext.newContext(formSection.getOwnerDocument());
			final Pointer pointer = 
			    context.getPointer("//*[@" + SchemaFormBuilder.XFORMS_NS_PREFIX + 
					       "bind='" + bindId + "']");
			if (pointer != null)
			    control = (Element) pointer.getNode();
		    }

		    //copy it
		    if (control == null)
			LOGGER.warn("Corresponding control not found");
		    else 
		    {
			Element newControl = (Element) control.cloneNode(true);
			//set new Ids to XForm elements
			this.resetXFormIds(newControl);

			repeatContentWrapper.appendChild(newControl);
		    }

		} 
		else
		{ 
                    //add it normally
		    final String elementName = getElementName(element, xForm);

		    final String path = (pathToRoot.length() == 0
					 ? elementName
					 : pathToRoot + "/" + elementName);

		    final Element newDefaultInstanceElement = xForm.createElement(elementName);
		    defaultInstanceElement.appendChild(newDefaultInstanceElement);
		    if (element.getConstraintType() != XSConstants.VC_NONE)
		    {
			Node value = xForm.createTextNode(element.getConstraintValue());
			newDefaultInstanceElement.appendChild(value);
		    }

		    this.addElement(xForm,
				    modelSection,
				    newDefaultInstanceElement,
				    repeatContentWrapper,
				    schema,
				    element,
				    element.getTypeDefinition(),
				    path);
		}
	    } 
	    else 
	    { //XSWildcard -> ignore ?
		//LOGGER.warn("XSWildcard found in group from "+owner.getName()+" for pathToRoot="+pathToRoot);
	    }
	}

	if (LOGGER.isDebugEnabled())
	    LOGGER.debug("--- end of addGroup from owner=" + owner.getName());
    }

    /**
     * Add a repeat section if maxOccurs > 1.
     */
    private Element addRepeatIfNecessary(final Document xForm,
                                         final Element modelSection,
                                         final Element formSection,
                                         final XSTypeDefinition controlType,
                                         final Occurs o ,
                                         final String pathToRoot) 
    {

        // add xforms:repeat section if this element re-occurs
        //
        if (o.maximum == 1) 
	    return formSection;

	if (LOGGER.isDebugEnabled())
	    LOGGER.debug("DEBUG: AddRepeatIfNecessary for multiple element for type " + 
			 controlType.getName() + ", maxOccurs=" + o.maximum);
	
	final Element repeatSection = 
	    xForm.createElementNS(XFORMS_NS, SchemaFormBuilder.XFORMS_NS_PREFIX + "repeat");

	//bind instead of repeat
	//repeatSection.setAttributeNS(XFORMS_NS,SchemaFormBuilder.XFORMS_NS_PREFIX + "nodeset",pathToRoot);
	// bind -> last element in the modelSection
	Element bind = DOMUtil.getLastChildElement(modelSection);
	String bindId = null;

	if (bind != null && 
	    bind.getLocalName() != null && 
	    "bind".equals(bind.getLocalName())) 
	    bindId = bind.getAttributeNS(SchemaFormBuilder.XFORMS_NS, "id");
	else 
	{
	    LOGGER.warn("addRepeatIfNecessary: bind not found: "
			+ bind
			+ " (model selection name="
			+ modelSection.getNodeName()
			+ ")");

	    //if no bind is found -> modelSection is already a bind, get its parent last child
	    bind = DOMUtil.getLastChildElement(modelSection.getParentNode());

	    if (bind != null &&
		bind.getLocalName() != null && 
		"bind".equals(bind.getLocalName())) 
		bindId = bind.getAttributeNS(SchemaFormBuilder.XFORMS_NS, "id");
	    else 
		LOGGER.warn("addRepeatIfNecessary: bind really not found");
	}

	repeatSection.setAttributeNS(XFORMS_NS,
				     SchemaFormBuilder.XFORMS_NS_PREFIX + "bind",
				     bindId);
	this.setXFormsId(repeatSection);

	//appearance=full is more user friendly
	repeatSection.setAttributeNS(XFORMS_NS,
				     SchemaFormBuilder.XFORMS_NS_PREFIX + "appearance",
				     "full");

	//triggers
	this.addTriggersForRepeat(xForm,
				  formSection,
				  repeatSection,
				  o,
				  bindId);
	    
	final Element controlWrapper =
	    _wrapper.createControlsWrapper(repeatSection);
	formSection.appendChild(controlWrapper);

	//add a group inside the repeat?
	//            Element group = xForm.createElementNS(XFORMS_NS,
	//this.SchemaFormBuilder.XFORMS_NS_PREFIX + "group");
	//this.setXFormsId(group);
	//repeatSection.appendChild(group);
	//repeatSection = group;
        return repeatSection;
    }

    /**
     * if "createBind", a bind is created, otherwise bindId is used
     */
    private void addSimpleType(final Document xForm,
                               final Element modelSection,
			       Element formSection,
			       final XSModel schema,
                               final XSTypeDefinition controlType,
                               final String owningElementName,
                               final XSObject owner,
                               final String pathToRoot,
                               final Occurs o) 
    {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("addSimpleType for " + controlType.getName() + 
			 " (owningElementName=" + owningElementName + ")");

        // create the <xforms:bind> element and add it to the model.
        Element bindElement = xForm.createElementNS(XFORMS_NS, 
						    SchemaFormBuilder.XFORMS_NS_PREFIX + "bind");
        String bindId = this.setXFormsId(bindElement);
        bindElement.setAttributeNS(XFORMS_NS,
				   SchemaFormBuilder.XFORMS_NS_PREFIX + "nodeset",
				   pathToRoot);
        bindElement = (Element)modelSection.appendChild(bindElement);
        bindElement = startBindElement(bindElement, schema, controlType, o);

        // add a group if a repeat !
        if (owner instanceof XSElementDeclaration && o.maximum != 1) 
	{
            Element groupElement = this.createGroup(xForm, 
						    modelSection, 
						    formSection, 
						    (XSElementDeclaration) owner);
            //set content
            Element groupWrapper = groupElement;
            if (groupElement != modelSection)
                groupWrapper = _wrapper.createGroupContentWrapper(groupElement);
            formSection = groupWrapper;
        }

        //eventual repeat
        final Element repeatSection = this.addRepeatIfNecessary(xForm, 
								modelSection, 
								formSection, 
								controlType, 
								o, 
								pathToRoot);
	
        // create the form control element
        //put a wrapper for the repeat content, but only if it is really a repeat
        Element contentWrapper = repeatSection;

        if (repeatSection != formSection) 
	{
            //content of repeat
            contentWrapper = _wrapper.createGroupContentWrapper(repeatSection);

            //if there is a repeat -> create another bind with "."
            Element bindElement2 =
		xForm.createElementNS(XFORMS_NS, 
				      SchemaFormBuilder.XFORMS_NS_PREFIX + "bind");
            String bindId2 = this.setXFormsId(bindElement2);
            bindElement2.setAttributeNS(XFORMS_NS,
					SchemaFormBuilder.XFORMS_NS_PREFIX + "nodeset",
					".");
            bindElement.appendChild(bindElement2);

	    bindElement = bindElement2;
            bindId = bindId2;
        }

        String caption = createCaption(owningElementName);
        Element formControl = this.createFormControl(xForm,
						     caption,
						     controlType,
						     bindId,
						     bindElement,
						     o);
        Element controlWrapper = _wrapper.createControlsWrapper(formControl);
        contentWrapper.appendChild(controlWrapper);

        // if this is a repeatable then set ref to point to current element
        // not sure if this is a workaround or this is just the way XForms works...
        //
        if (!repeatSection.equals(formSection)) 
            formControl.setAttributeNS(XFORMS_NS,
				       SchemaFormBuilder.XFORMS_NS_PREFIX + "ref",
				       ".");

        Element hint = createHint(xForm, owner);
        if (hint != null)
            formControl.appendChild(hint);

        //add selector if repeat
        //if (repeatSection != formSection)
        //this.addSelector(xForm, (Element) formControl.getParentNode());
        //
        // TODO: Generate help message based on datatype and restrictions
        endFormControl(formControl, controlType, o);
        endBindElement(bindElement);
    }

    private void addSimpleType(final Document xForm,
                               final Element modelSection,
                               final Element formSection,
			       final XSModel schema,
                               final XSSimpleTypeDefinition controlType,
                               final XSElementDeclaration owner,
                               final String pathToRoot) 
    {
        this.addSimpleType(xForm,
			   modelSection,
			   formSection,
			   schema,
			   controlType,
			   owner.getName(),
			   owner,
			   pathToRoot,
			   this.getOccurance(owner));
    }

    private void addSimpleType(final Document xForm,
                               final Element modelSection,
                               final Element formSection,
			       final XSModel schema,
                               final XSSimpleTypeDefinition controlType,
                               final XSAttributeUse owningAttribute,
                               final String pathToRoot) 
    {
        this.addSimpleType(xForm,
			   modelSection,
			   formSection,
			   schema,
			   controlType,
			   owningAttribute.getAttrDeclaration().getName(),
			   owningAttribute,
			   pathToRoot,
			   new Occurs(owningAttribute.getRequired() ? 1 : 0, 1));
    }

    private Element createTriggerForRepeat(final Document xForm,
					   final String id,
					   final String label,
					   final Element action)
    {
        final Element trigger =
	    xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "trigger");
        this.setXFormsId(trigger, id != null ? id : null);

        //label insert
        final Element triggerLabel =
	    xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "label");
        this.setXFormsId(triggerLabel);
        trigger.appendChild(triggerLabel);
        //triggerLabel_insert.setAttributeNS(SchemaFormBuilder.XLINK_NS,
	//				   SchemaFormBuilder.XLINK_NS_PREFIX + "href",
	//				   "images/add_new.gif");

        triggerLabel.appendChild(xForm.createTextNode(label));

        //hint insert
        //Element hint_insert =
        //        xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
        //                SchemaFormBuilder.XFORMS_NS_PREFIX + "hint");
        //this.setXFormsId(hint_insert);
        //Text hint_insert_text =
        //        xForm.createTextNode("inserts a new entry in this collection");
        //hint_insert.appendChild(hint_insert_text);
        //trigger_insert.appendChild(hint_insert);

        //insert action
        final Element actionWrapper =
	    xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "action");
	actionWrapper.appendChild(action);
        trigger.appendChild(action);
        this.setXFormsId(action);

	return trigger;
    }
				  

    /**
     * add triggers to use the repeat elements (allow to add an element, ...)
     */
    private void addTriggersForRepeat(final Document xForm,
                                      final Element formSection,
                                      final Element repeatSection,
                                      final Occurs o,
                                      final String bindId) {
        //xforms:at = xforms:index from the "id" attribute on the repeat element
        final String repeatId =
	    repeatSection.getAttributeNS(SchemaFormBuilder.XFORMS_NS, "id");

        ///////////// insert //////////////////
        //trigger insert

	Element action =
	    xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "insert");
        //insert: bind & other attributes
        if (bindId != null)
            action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "bind",
				  bindId);

        action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
			      SchemaFormBuilder.XFORMS_NS_PREFIX + "position",
			      "before");

        if (repeatId != null)
            action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "at",
				  "1");

        final Element trigger_insert_before =
	    this.createTriggerForRepeat(xForm, 
					repeatId != null ? repeatId + "-insert_before" : null,
					"insert at beginning", 
					action);


	action = xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
				       SchemaFormBuilder.XFORMS_NS_PREFIX + "insert");

        //insert: bind & other attributes
        if (bindId != null)
            action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "bind",
				  bindId);

        action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
			      SchemaFormBuilder.XFORMS_NS_PREFIX + "position",
			      "after");

        if (repeatId != null)
            action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "at",
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "index('" + repeatId + "')");
					
	final Element trigger_insert_after =
	    this.createTriggerForRepeat(xForm, 
					repeatId != null ? repeatId + "-insert_after" : null,
					"insert after selected", 
					action);

        ///////////// delete //////////////////
        //trigger delete
        action = xForm.createElementNS(SchemaFormBuilder.XFORMS_NS,
				       SchemaFormBuilder.XFORMS_NS_PREFIX + "delete");

        //delete: bind & other attributes
        if (bindId != null)
            action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "bind",
				  bindId);

        //xforms:at = xforms:index from the "id" attribute on the repeat element
        if (repeatId != null)
	    action.setAttributeNS(SchemaFormBuilder.XFORMS_NS,
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "at",
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "index('" + repeatId + "')");

        final Element trigger_delete =
	    this.createTriggerForRepeat(xForm, 
					repeatId != null ? repeatId + "-delete" : null,
					"delete selected", 
					action);


        //add the triggers
        final Element wrapper_triggers =
	    _wrapper.createControlsWrapper(trigger_insert_before);

        if (wrapper_triggers == trigger_insert_before) 
	{
	    //no wrapper
            formSection.appendChild(trigger_insert_before);
            formSection.appendChild(trigger_insert_after);
            formSection.appendChild(trigger_delete);
        } 
	else 
	{
            formSection.appendChild(wrapper_triggers);
            final Element insert_parent = (Element)trigger_insert_before.getParentNode();

            if (insert_parent != null)
	    {
		insert_parent.appendChild(trigger_insert_after);
                insert_parent.appendChild(trigger_delete);
	    }
        }
    }

    private void buildTypeTree(final XSTypeDefinition type, 
			       final TreeSet descendents) 
    {
        if (type == null) 
	    return;

	if (descendents.size() > 0) 
	{
	    //TreeSet compatibleTypes = (TreeSet) typeTree.get(type.getName());
	    TreeSet<XSTypeDefinition> compatibleTypes = this.typeTree.get(type.getName());
	    
	    if (compatibleTypes == null) 
	    {
		//compatibleTypes = new TreeSet(descendents);
		compatibleTypes = new TreeSet<XSTypeDefinition>(this.typeExtensionSorter);
		this.typeTree.put(type.getName(), compatibleTypes);
	    }
	    compatibleTypes.addAll(descendents);
	}

	final XSTypeDefinition parentType = type.getBaseType();
	
	if (parentType == null ||
	    type.getTypeCategory() != parentType.getTypeCategory()) 
	    return;
	if (type != parentType && 
	    (parentType.getName() == null || !parentType.getName().equals("anyType"))) 
	{
	    
	    //TreeSet newDescendents=new TreeSet(descendents);
	    final TreeSet<XSTypeDefinition> newDescendents = 
		new TreeSet<XSTypeDefinition>(this.typeExtensionSorter);
	    newDescendents.addAll(descendents);
	    
//extension (we only add it to "newDescendants" because we don't want
//to have a type descendant to itself, but to consider it for the parent
	    if (type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) 
	    {
		final XSComplexTypeDefinition complexType = (XSComplexTypeDefinition)type;
		if (complexType.getDerivationMethod() == XSConstants.DERIVATION_EXTENSION && 
		    !complexType.getAbstract() && 
		    !descendents.contains(type)) 
		    newDescendents.add(type);
	    }
//note: extensions are impossible on simpleTypes !
	    
	    this.buildTypeTree(parentType, newDescendents);
        }
    }

    private void buildTypeTree(final XSModel schema) 
    {
	LOGGER.debug("buildTypeTree " + schema);
        // build the type tree for complex types
        final XSNamedMap types = schema.getComponents(XSConstants.TYPE_DEFINITION);
        for (int i = 0; i < types.getLength(); i++) 
	{
            final XSTypeDefinition t = (XSTypeDefinition) types.item(i);
            if (t.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) 
	    {
                final XSComplexTypeDefinition type = (XSComplexTypeDefinition)t;
                this.buildTypeTree(type, 
				   new TreeSet<XSTypeDefinition>(this.typeExtensionSorter));
            }
        }

        // build the type tree for simple types
        for (int i = 0; i < types.getLength(); i++) {
            final XSTypeDefinition t = (XSTypeDefinition) types.item(i);
            if (t.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) 
	    {
                this.buildTypeTree((XSSimpleTypeDefinition)t, 
				   new TreeSet<XSTypeDefinition>(this.typeExtensionSorter));
            }
        }

        // print out type hierarchy for debugging purposes
        if (true || LOGGER.isDebugEnabled()) 
	{
            for (String typeName : this.typeTree.keySet())
	    {
                TreeSet descendents = this.typeTree.get(typeName);
                LOGGER.debug(">>>> for " + typeName + " Descendants=\n ");
                Iterator it = descendents.iterator();
                while (it.hasNext()) 
		{
                    XSTypeDefinition desc = (XSTypeDefinition) it.next();
                    LOGGER.debug("      " + desc.getName());
                }
            }
        }
    }

    private Element createFormControl(final Document xForm,
                                      final String caption,
                                      final XSTypeDefinition controlType,
                                      final String bindId,
                                      final Element bindElement,
                                      final Occurs o) 
    {
        // Select1 xform control to use:
        // Will use one of the following: input, textarea, selectOne, selectBoolean, selectMany, range
        // secret, output, button, do not apply
        //
        // select1: enumeration or keyref constrained value
        // select: list
        // range: union (? not sure about this)
        // textarea : ???
        // input: default
        //
        Element formControl = null;

        if (controlType.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) 
	{
            XSSimpleTypeDefinition simpleType =
                    (XSSimpleTypeDefinition) controlType;
            if (simpleType.getItemType() != null)
            {
		//list
                formControl = createControlForListType(xForm,
						       simpleType,
						       caption,
						       bindElement);
            } 
	    else
	    {
		//other simple type
                // need to check constraints to determine which form control to use
                //
                // use the selectOne control
                //
                if (simpleType.isDefinedFacet(XSSimpleTypeDefinition.FACET_ENUMERATION)) 
                    formControl = createControlForEnumerationType(xForm,
								  simpleType,
								  caption,
								  bindElement);
            }
        } 
	else if (controlType.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE && 
		 "anyType".equals(controlType.getName())) 
	{
            formControl = createControlForAnyType(xForm, caption, controlType);
        }

        if (formControl == null)
            formControl = createControlForAtomicType(xForm,
						     caption,
						     (XSSimpleTypeDefinition)controlType);

        startFormControl(formControl, controlType);
        formControl.setAttributeNS(XFORMS_NS, 
				   SchemaFormBuilder.XFORMS_NS_PREFIX + "bind", 
				   bindId);

        // TODO: Enhance alert statement based on facet restrictions.
        // TODO: Enhance to support minOccurs > 1 and maxOccurs > 1.
        // TODO: Add i18n/l10n suppport to this - use java MessageFormatter...
        //
        //       e.g. Please provide a valid value for 'Address'. 'Address' is a mandatory decimal field.
        //
//        Element alertElement = (Element) 
//	    formControl.appendChild(xForm.createElementNS(XFORMS_NS,
//							  SchemaFormBuilder.XFORMS_NS_PREFIX + "alert"));
//        this.setXFormsId(alertElement);
//
//        StringBuffer alert =
//	    new StringBuffer("Please provide a valid value for '" + caption + "'.");
//
//        Element enveloppe = xForm.getDocumentElement();
//	alert.append(" '" + caption + 
//		     "' is " + (o.minimum == 0 ? "an optional" : "a required") + " '" + 
//		     createCaption(this.getXFormsTypeName(enveloppe, controlType)) + 
//		     "' value.");
//        alertElement.appendChild(xForm.createTextNode(alert.toString()));
        return formControl;
    }

    /**
     * used to get the type name that will be used in the XForms document
     *
     * @param context     the element which will serve as context for namespaces
     * @param controlType the type from which we want the name
     * @return the complete type name (with namespace prefix) of the type in the XForms doc
     */
    protected String getXFormsTypeName(final Element context,
				       final XSModel schema,
				       final XSTypeDefinition controlType) 
    {
        final String typeName = controlType.getName();
        final String typeNS = controlType.getNamespace();

        //if we use XMLSchema types:
        //first check if it is a simple type named in the XMLSchema
        if (controlType.getTypeCategory() != XSTypeDefinition.SIMPLE_TYPE ||
	    typeName == null || 
	    typeName.length() == 0 ||
	    schema.getTypeDefinition(typeName, typeNS) == null)
	{
	    //use built in type
	    return this.getDataTypeName(getBuiltInType(controlType));
	}

	//type is globally defined
	//use schema type

	//local type name
	String localTypeName = typeName;
	int index = typeName.indexOf(":");
	if (index > -1 && typeName.length() > index)
	    localTypeName = typeName.substring(index + 1);
	
	//namespace prefix in this document
	String prefix = NamespaceCtx.getPrefix(context, typeNS);
	
	//completeTypeName = new prefix + local name
	String result = localTypeName;
	if (prefix != null && prefix.length() != 0)
	    result = prefix + ":" + localTypeName;
	
	if (LOGGER.isDebugEnabled())
	    LOGGER.debug("getXFormsTypeName: typeName=" + typeName + 
			 ", typeNS=" + typeNS + 
			 ", result=" + result);
	return result;
    }

    private Document createFormTemplate(final String formId)
    {
	final TemplatingService ts = TemplatingService.getInstance();
        final Document xForm = ts.newDocument();

        final Element envelopeElement = _wrapper.createEnvelope(xForm);

	final Map<String, String> namespaces = new HashMap<String, String>();
	namespaces.put(SchemaFormBuilder.CHIBA_NS_PREFIX, SchemaFormBuilder.CHIBA_NS);
	namespaces.put(SchemaFormBuilder.XFORMS_NS_PREFIX, SchemaFormBuilder.XFORMS_NS);
	namespaces.put(SchemaFormBuilder.XLINK_NS_PREFIX, SchemaFormBuilder.XLINK_NS);
	namespaces.put(SchemaFormBuilder.XMLEVENTS_NS_PREFIX, SchemaFormBuilder.XMLEVENTS_NS);
	namespaces.put(SchemaFormBuilder.XMLSCHEMA_INSTANCE_NS_PREFIX, 
		       SchemaFormBuilder.XMLSCHEMA_INSTANCE_NS);
	for (String nsPrefix : namespaces.keySet())
	{
	    this.addNamespace(envelopeElement, nsPrefix, namespaces.get(nsPrefix));
	}

	//base
        if (_base != null && _base.length() != 0)
            envelopeElement.setAttributeNS(XML_NAMESPACE_URI, "xml:base", _base);

        //model element
        Element modelElement = xForm.createElementNS(XFORMS_NS, 
						     SchemaFormBuilder.XFORMS_NS_PREFIX + "model");
        this.setXFormsId(modelElement);
        Element modelWrapper = _wrapper.createModelWrapper(modelElement);
        envelopeElement.appendChild(modelWrapper);

        //form control wrapper -> created by wrapper
        //Element formWrapper = xForm.createElement("body");
        //envelopeElement.appendChild(formWrapper);
        Element formWrapper = _wrapper.createFormWrapper(envelopeElement);

        return xForm;
    }

    private Element createGroup(Document xForm,
                                Element modelSection,
                                Element formSection,
                                XSElementDeclaration owner) {
        // add a group node and recurse
	Element groupElement =
	    xForm.createElementNS(XFORMS_NS, 
				  SchemaFormBuilder.XFORMS_NS_PREFIX + "group");
        groupElement = startFormGroup(groupElement, owner);

        if (groupElement == null)
	    groupElement = modelSection;
	else
	{
            this.setXFormsId(groupElement);

            Element controlsWrapper = _wrapper.createControlsWrapper(groupElement);

            //groupElement = (Element) formSection.appendChild(groupElement);
            formSection.appendChild(controlsWrapper);

            Element captionElement =
		xForm.createElementNS(XFORMS_NS,
				      SchemaFormBuilder.XFORMS_NS_PREFIX + "label");
	    groupElement.appendChild(captionElement);
            this.setXFormsId(captionElement);
            captionElement.appendChild(xForm.createTextNode(createCaption(owner)));
        }
        return groupElement;
    }

    /**
     * Get a fully qualified name for this element, and eventually declares a new prefix for the namespace if
     * it was not declared before
     *
     * @param element
     * @param xForm
     * @return
     */
    private String getElementName(final XSElementDeclaration element, 
				  final Document xForm) 
    {
        String elementName = element.getName();
        String namespace = element.getNamespace();
        if (namespace != null && namespace.length() != 0) 
	{
            String prefix;
            if ((prefix = (String) namespacePrefixes.get(namespace)) == null) 
	    {
                String basePrefix = (namespace.substring(namespace.lastIndexOf('/', namespace.length()-2)+1));
                int i=1;
                prefix = basePrefix;
                while (namespacePrefixes.containsValue(prefix)) 
		{
                    prefix = basePrefix + (i++);
                }
                namespacePrefixes.put(namespace, prefix);
                Element envelope = xForm.getDocumentElement();
                envelope.setAttributeNS(XMLNS_NAMESPACE_URI, "xmlns:" + prefix, namespace);
            }
            elementName = prefix + ":" + elementName;
        }
        return elementName;
    }

    private XSModel loadSchema(final TemplateType tt)
	throws FormBuilderException
    {
	try
	{
	    // Get DOM Implementation using DOM Registry
	    System.setProperty(DOMImplementationRegistry.PROPERTY,
			       "org.apache.xerces.dom.DOMXSImplementationSourceImpl");

	    final DOMImplementationRegistry registry =
		DOMImplementationRegistry.newInstance();

	    final DOMImplementationLS lsImpl = (DOMImplementationLS)
		registry.getDOMImplementation("XML 1.0 LS 3.0");
	    final TemplatingService ts = TemplatingService.getInstance();
	    final LSInput in = lsImpl.createLSInput();
	    in.setStringData(ts.writeXMLToString(tt.getSchema()));

	    final XSImplementation xsImpl = (XSImplementation)
		registry.getDOMImplementation("XS-Loader");
	    final XSLoader schemaLoader = xsImpl.createXSLoader(null);
	    return schemaLoader.load(in);
        } catch (ClassNotFoundException x) {
            throw new FormBuilderException(x);
        } catch (InstantiationException x) {
            throw new FormBuilderException(x);
        } catch (IllegalAccessException x) {
            throw new FormBuilderException(x);
        }
    }

    private void addNamespace(final Element e,
			      final String nsPrefix,
			      final String ns)
    {
	final String p = nsPrefix.substring(0, nsPrefix.length() - 1);
	if (!e.hasAttributeNS(XMLNS_NAMESPACE_URI, p))
	    e.setAttributeNS(XMLNS_NAMESPACE_URI, "xmlns:" + p, ns);
    }
}
