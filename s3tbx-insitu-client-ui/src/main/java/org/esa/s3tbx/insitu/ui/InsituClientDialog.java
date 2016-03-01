package org.esa.s3tbx.insitu.ui;

import org.esa.snap.tango.TangoIcons;
import org.esa.snap.ui.ModelessDialog;
import org.esa.snap.ui.UIUtils;
import org.esa.snap.ui.tool.ToolButtonFactory;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import java.awt.Window;

/**
 * @author Marco Peters
 */
public class InsituClientDialog extends ModelessDialog {

    private InsituClientModel model;

    public InsituClientDialog(Window parent, String title, String helpID) {
        super(parent, title, ID_OK | ID_CANCEL | ID_HELP, helpID);
        final ImageIcon imageIcon = UIUtils.loadImageIcon("/org/esa/s3tbx/insitu/insitu.png", InsituClientDialog.class);
        if (imageIcon != null) {
            getJDialog().setIconImage(imageIcon.getImage());
        }

        getButton(ID_OK).setText("Download");
        getButtonPanel().add(new JLabel("#Obs: 486"), 0);
        final AbstractButton refreshButton = ToolButtonFactory.createButton(TangoIcons.actions_view_refresh(TangoIcons.Res.R22), false);
        getButtonPanel().add(refreshButton, 0);

        model = new InsituClientModel();
        setContent(new InsituClientForm(model));
    }

    @Override
    protected void onOK() {
        hide();
    }

    @Override
    public void onCancel() {
        hide();
    }

    @Override
    public void hide() {
        super.hide();
        model.dispose();
    }

    @Override
    public int show() {
        return super.show();
    }

}
