/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.tools.xjc.api;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import com.sun.tools.xjc.ConsoleErrorReporter;
import com.sun.xml.bind.v2.WellKnownNamespace;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.opts.BooleanOption;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Tests the XJC API.
 * 
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class Driver {
    
    private static class ErrorReceiverImpl extends ConsoleErrorReporter implements ErrorListener {}
    
    /** Use StAX? Otherwise use SAX. */
    public BooleanOption stax = new BooleanOption("-stax");
    /** Generate code and dump it to screen? */
    public BooleanOption code = new BooleanOption("-code");
    
    private File[] files;
    
    private Driver( String[] args ) throws CmdLineException {
        CmdLineParser p = new CmdLineParser();
        p.addOptionClass(this);
        p.parse(args);
        
        List<String> files = p.getArguments();
        this.files = new File[files.size()];
        int i=0;
        for (String f : files) {
            this.files[i++] = new File(f);
        }
    }
    
    public static void main(String[] args) throws Exception {
        new Driver(args).run();
    }
    
    private void run() throws Exception {
        SchemaCompiler compiler = XJC.createSchemaCompiler();
        
        ErrorReceiverImpl er = new ErrorReceiverImpl();
        compiler.setErrorListener(er);

//        XMLInputFactory xif = new MXParserFactory();  // BEA's RI
        XMLInputFactory xif = new com.sun.xml.stream.ZephyrParserFactory();

        for (File value : files) {
            String url = value.toURL().toString();

            if (value.getName().toLowerCase().endsWith(".wsdl")) {
                // read it as WSDL.
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                Document dom = dbf.newDocumentBuilder().parse(value);
                compiler.parseSchema(url,
                        findSchemas(dom.getDocumentElement()));
            } else if (stax.isOn()) {
                XMLStreamReader r = xif.createXMLStreamReader(new FileInputStream(value));

                // workaround for bug 5030916 in Zephyr
                if (r.getEventType() != XMLStreamConstants.START_DOCUMENT)
                    r.next();

                System.err.println("parsing " + value);
                compiler.parseSchema(url, r);
            } else {
                compiler.parseSchema(new InputSource(url));
            }
        }
        
        S2JJAXBModel model = compiler.bind();
        if(model==null) {
            System.out.println("failed to compile.");
            return;
        }
        
        dumpModel(model);
        
        // build the code, just to see if there's any error
        JCodeModel cm = model.generateCode(null, er);
        
        if(code.isOn()) {
            cm.build(new SingleStreamCodeWriter(System.out));
        }
    }

    private Element findSchemas(Element e) {
        NodeList children = e.getChildNodes();
        for( int i=0; i<children.getLength(); i++ ) {
            Node n = children.item(i);
            if( n.getNodeType()==Node.ELEMENT_NODE ) {
                Element x = (Element)n;
                if(x.getLocalName().equals("schema")
                && x.getNamespaceURI().equals(WellKnownNamespace.XML_SCHEMA))
                    return x;
                
                x = findSchemas(x);
                if(x!=null) return x;
            }
        }
        return null;
    }

    /**
     * Dumps a {@link JAXBModel}.
     */
    public static void dumpModel(S2JJAXBModel model) {
        System.out.println("--- class list ---");
        for( String s : model.getClassList() )
            System.out.println("  "+s);
        System.out.println();

//        System.out.println("--- XML type -> Java type ---");
//        for( Map.Entry<QName,String> e : model.getXmlTypeNameToJavaTypeNameMap().entrySet() )
//            System.out.println("  "+e.getKey()+"->"+e.getValue());
//        System.out.println();
//
//        System.out.println("--- Java type -> XML type ---");
//        for( Map.Entry<String,QName> e : model.getJavaTypeNameToXmlTypeNameMap().entrySet() )
//            System.out.println("  "+e.getKey()+"->"+e.getValue());
//        System.out.println();

        for( Mapping m  : model.getMappings() ) {
            System.out.println(m.getElement()+"<->"+m.getType());

            List<? extends Property> detail = m.getWrapperStyleDrilldown();
            if(detail==null) {
                System.out.println("(not a wrapper-style element)");
            } else {
                for(Property p : detail ) {
                    System.out.println("  "+p.name()+'\t'+p.type()+'\t'+p.elementName());
                }
            }

            System.out.println();
        }
    }
}
