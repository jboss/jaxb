package com.sun.tools.xjc.model;

import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.NormalizedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.model.nav.EagerNClass;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.tools.xjc.model.nav.NType;
import com.sun.tools.xjc.model.nav.NavigatorImpl;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.bind.v2.model.core.Adapter;

/**
 * Extended {@link Adapter} for use within XJC.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CAdapter extends Adapter<NType,NClass> {

    /**
     * If non-null, the same as {@link #adapterType} but more conveniently typed.
     */
    private JClass adapterClass1;

    /**
     * If non-null, the same as {@link #adapterType} but more conveniently typed.
     */
    private Class<? extends XmlAdapter> adapterClass2;

    /**
     * When the adapter class is statically known to us.
     *
     * @param copy
     *      true to copy the adapter class into the user package,
     *      or otherwise just refer to the class specified via the
     *      adapter parameter.
     */
    public CAdapter(Class<? extends XmlAdapter> adapter, boolean copy) {
        super(getRef(adapter,copy),NavigatorImpl.theInstance);
        this.adapterClass1 = null;
        this.adapterClass2 = adapter;
    }

    static NClass getRef( final Class<? extends XmlAdapter> adapter, boolean copy ) {
        if(copy) {
            // TODO: this is a hack. the code generation should be defered until
            // the backend. (right now constant generation happens in the front-end)
            return new EagerNClass(adapter) {
                @Override
                public JClass toType(Outline o, Aspect aspect) {
                    return o.addRuntime(adapter);
                }
                public String fullName() {
                    // TODO: implement this method later
                    throw new UnsupportedOperationException();
                }
            };
        } else {
            return NavigatorImpl.theInstance.ref(adapter);
        }
    }

    public CAdapter(JClass adapter) {
        super( NavigatorImpl.theInstance.ref(adapter), NavigatorImpl.theInstance);
        this.adapterClass1 = adapter;
        this.adapterClass2 = null;
    }

    public JClass getAdapterClass(JCodeModel cm) {
        if(adapterClass1==null)
            adapterClass1 = cm.ref(adapterClass2);
        return adapterClass1;
    }

    /**
     * Returns true if the adapter is for whitespace normalization.
     * Such an adapter can be ignored when producing a list.
     */
    public boolean isWhitespaceAdapter() {
        return adapterClass2==CollapsedStringAdapter.class || adapterClass2==NormalizedStringAdapter.class;
    }

    /**
     * Returns the adapter class if the adapter type is statically known to XJC.
     * <p>
     * This method is mostly for enabling certain optimized code generation.
     */
    public Class<? extends XmlAdapter> getAdapterIfKnown() {
        return adapterClass2;
    }
}