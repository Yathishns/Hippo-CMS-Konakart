package org.onehippo.forge.konakart.hst.tags;

import org.hippoecm.hst.core.component.HstURL;
import org.hippoecm.hst.tag.HstActionURLTag;
import org.onehippo.forge.konakart.hst.beans.KKProductDocument;
import org.onehippo.forge.konakart.hst.utils.KKCheckoutConstants;

import javax.servlet.jsp.tagext.TagData;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.VariableInfo;

public class KKAddToBasketActionURLTag extends HstActionURLTag {


    private KKProductDocument kkProductDocument;

    public void setKkProductDocument(KKProductDocument kkProductDocument) {
        this.kkProductDocument = kkProductDocument;
    }

    @Override
    protected void setUrlParameters(HstURL url) {
        super.setUrlParameters(url);

        super.addParameter("action", KKCheckoutConstants.ACTIONS.ADD_TO_BASKET.name());
        super.addParameter("prodId", String.valueOf(kkProductDocument.getProductId()));
    }

    /**
     * TagExtraInfo class for HstURLTag.
     */
    public static class TEI extends TagExtraInfo {

        public VariableInfo[] getVariableInfo(TagData tagData) {
            VariableInfo vi[] = null;
            String var = tagData.getAttributeString("var");
            if (var != null) {
                vi = new VariableInfo[1];
                vi[0] =
                        new VariableInfo(var, "java.lang.String", true,
                                VariableInfo.AT_BEGIN);
            }
            return vi;
        }

    }
}
