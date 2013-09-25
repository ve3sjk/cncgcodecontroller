/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cnc.gcode.controller;

import com.sun.org.apache.bcel.internal.generic.CALOAD;
import com.sun.swing.internal.plaf.metal.resources.metal;
import de.unikassel.ann.util.ColorHelper;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import sun.print.PSPrinterJob;

/**
 *
 * @author patrick
 */
public class JPanelAutoLevel extends javax.swing.JPanel implements IGUIEvent {

    private IEvent GUIEvent=null;
    
    private abstract class PMySwingWorker<R, P> extends MySwingWorker<R, P>
    {
        @Override
        protected final void progress(int progress, String message) {
            jPBar.setValue(progress);
            jPBar.setString(message);
        }
    }

    public AutoLevelSystem al=new AutoLevelSystem();
    
    
    NumberFildManipulator[][] axes;
    private PMySwingWorker worker=null;
    private BufferedImage image;
    private AffineTransform trans=new AffineTransform();
    boolean hit=false;
    double hitvalue=0;
    
    private final TriggertSwingWorker<BufferedImage> painter =new TriggertSwingWorker<BufferedImage>() {
            class GetDataSyncedHelper
            {
                private int jpw;
                private int jph;
                private AutoLevelSystem al;
            }
            
            @Override
            protected BufferedImage doJob() throws Exception {
                
                //Load Parameter:
                final GetDataSyncedHelper data= new GetDataSyncedHelper();
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        data.jpw=jPPaint.getWidth();
                        data.jph=jPPaint.getHeight();
                        data.al=al;
                    }
                });        
                
                if(data.jpw<=0) data.jpw=1;
                if(data.jph<=0) data.jph=1;
                    
                BufferedImage image= new BufferedImage(data.jpw, data.jph, BufferedImage.TYPE_4BYTE_ABGR);
                
                Graphics2D g2=image.createGraphics();
                
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                
                Graphics guntransformed= g2.create();
                guntransformed.setColor(Color.black);
                guntransformed.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                
                //Scalling transforming ...
                //StartCorner
                g2.translate(data.jpw/2, data.jph/2);
                switch(Integer.parseInt(Database.HOMEING.get()))
                {
                    case 0:
                    default:
                        g2.scale(1,1);                
                        break;
                    case 1:
                        g2.scale(-1,1);
                        break;
                    case 2:
                        g2.scale(1,-1); 
                        break;
                    case 3:                
                        g2.scale(-1,-1);
                        break;
                }
                //Zoom
                g2.translate(-data.jpw/2, -data.jph/2);
                
                //Display Position
                double ariawidth= Database.WORKSPACE0.getsaved(); //x
                double ariaheight= Database.WORKSPACE1.getsaved(); //y
                Rectangle rect=Geometrics.placeRectangle(data.jpw, Math.abs(data.jph-20), Geometrics.getRatio(ariawidth,ariaheight));
                rect.setLocation(rect.x, rect.y+10);
                double scalex=rect.width/ariawidth;
                double scaley=rect.height/ariaheight;
                g2.translate(rect.x, rect.y);
                g2.scale(scalex, scaley);
                
                //Draw base
                g2.setColor(Color.white);
                g2.fill(new Rectangle2D.Double(0, 0, ariawidth, ariaheight));
                
                try {
                    AffineTransform t=g2.getTransform();
                    t.invert();
                    trans=t;
                } catch (NoninvertibleTransformException ex) {
                    trans=new AffineTransform();
                }
                
                double d=Math.min(Database.ALDISTANACE.getsaved()/10,10);
                if(data.al.isLeveled())
                {
                    double max=-Double.MAX_VALUE;
                    double min= Double.MAX_VALUE;
                    for(AutoLevelSystem.Point p:data.al.getPoints())
                    {

                        if(min>p.getValue())
                            min=p.getValue();
                        if(max<p.getValue())
                            max=p.getValue();
                    }
                    double delta=max-min;
                    g2.setTransform(new AffineTransform());
                    int cx=Math.max((int)(Database.WORKSPACE0.getsaved()/Database.ALDISTANACE.getsaved()*10),rect.width);
                    int cy=Math.max((int)(Database.WORKSPACE1.getsaved()/Database.ALDISTANACE.getsaved()*10),rect.height);
                    double w=rect.width/(double)cx;
                    double h=rect.width/(double)cy;
                    for(int x=0;x<cx;x++)
                    {
                        for(int y=0;y<cy;y++)
                        {
                            Point2D p=trans.transform(new Point2D.Double((double)rect.x+ (x+0.5)*w, (double)rect.y+ (y+0.5)*h), null);
                            double z=data.al.getdZ(p);
                            double relative=(z-min)/delta;
                            if(relative>1) 
                                relative=1;
                            if(relative<0)
                                relative=0;
                            g2.setColor(ColorHelper.numberToColorPercentage(relative));
                            g2.fillRect((int)Math.floor(rect.x+ x*w), (int)Math.floor(rect.y+ y*h),(int)Math.ceil(w),(int)Math.ceil(h));
                        }
                    }
                }
                else
                {
                    for(AutoLevelSystem.Point p:data.al.getPoints())
                    {
                        if(p.isLeveld())
                            g2.setColor(Color.green);
                        else
                            g2.setColor(Color.black);

                        g2.fill(new Ellipse2D.Double(p.getPoint().x - d / 2, p.getPoint().y - d / 2, d, d));

                    }
                }
                return image;
            }

            @Override
            protected void process(BufferedImage chunk) {
                image=chunk;
                
                jPPaint.repaint();
            }
            
        };

    
    
    /**
     * Creates new form JPanelAutoLevel
     */
    public JPanelAutoLevel() {
        initComponents();
        
        NumberFildManipulator.IAxesEvent axesevent= new NumberFildManipulator.IAxesEvent() {
            @Override
            public void fired(NumberFildManipulator axis) {
                double value;
                try {
                   value=axis.getd();
                } catch (ParseException ex) {
                    axis.popUpToolTip(ex.toString());
                    axis.setFocus();
                    return;
                }

                //Write back Value
                axis.set(value);

                //Check Values
                for(int i=0;i<2;i++)
                {
                    for(NumberFildManipulator n:axes[i])
                    {
                        double v=n.getdsave();
                        if(v<0)
                        {
                            n.set(0.0);
                            n.setFocus();
                            n.popUpToolTip("Value must be bigger then zero");
                        }
                        if(v>Database.getWorkspace(i).getsaved())
                        {
                            n.set(Database.getWorkspace(i).getsaved());
                            n.setFocus();
                            n.popUpToolTip("Value must be smaler then "+Database.getWorkspace(i));
                        }
                    }
                    if(axes[i][0].getdsave()>axes[i][1].getdsave())
                    {
                            axes[i][1].set(axes[i][0].getdsave());
                            axes[i][1].setFocus();
                            axes[i][1].popUpToolTip("Value must be bigger then start value");
                    }
                }
                
                makeNewAl();
            }

        };
        
        axes= new NumberFildManipulator[][]{
                                    /*0 START*/                                             /*0 END*/
                /*0 X*/ { new NumberFildManipulator(jTFStartX, axesevent), new NumberFildManipulator(jTFEndX, axesevent), },
                /*1 Y*/ { new NumberFildManipulator(jTFStartY, axesevent), new NumberFildManipulator(jTFEndY, axesevent), },
            };
        
        for(int i=0;i<2;i++)
        {
                axes[i][0].set(0.0);
                axes[i][1].set(Database.getWorkspace(i).getsaved());
        }
        makeNewAl();

        
        jPPaint.addPaintEventListener(new JPPaintableListener() {
            @Override
            public void paintComponent(JPPaintableEvent evt) {
                if(image!=null)
                    evt.getGaraphics().drawImage(image, 0, 0, null);
            }
        });
        
       Communication.getInstance().addResiveEvent(new Communication.IResivedLines() {
            @Override
            public void resived(String[] lines) {
                for(String line: lines)
                {
                    //echo:endstops hit:  Z:0.00
                            
                    if(line.contains("endstops") && line.contains("hit") && line.contains("Z:"))
                    {
                        try {
                            hitvalue=Tools.strtod(line.substring(line.indexOf("Z:")+2));
                            hit=true;  
                            worker.trigger();
                        } catch (ParseException ex) {
                            Logger.getLogger(JPanelAutoLevel.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    
                }
            }
        });
        
        
    }

    @Override
    public void setGUIEvent(IEvent event) {
        GUIEvent=event;
    }

    @Override
    public void updateGUI(boolean serial, boolean isworking) {
        jTFStartX.setEnabled(!isWorking() && !al.isLeveled());
        jTFStartY.setEnabled(!isWorking() && !al.isLeveled());
        jTFEndX.setEnabled(!isWorking() && !al.isLeveled());
        jTFEndY.setEnabled(!isWorking() && !al.isLeveled());

        jBAction.setEnabled((!isworking && serial) || isWorking());
        if(isLeveled())
            jBAction.setText("Clear");
        else if(isWorking())
            jBAction.setText("Abrot");
        else
            jBAction.setText("Start");
        
        jBPause.setEnabled(isWorking());
        jBPause.setText((isWorking() && worker.isPuased())?"Resume":"Pause");

        if(!isWorking())
        {
            jPBar.setValue(0);
            jPBar.setString("");
        }
        
        painter.trigger();
    }
    
    private void fireupdateGUI()
    {
        if(GUIEvent==null)
            throw new RuntimeException("GUI EVENT NOT USED!");
        GUIEvent.fired();
    }
    
    public boolean isLeveled()
    {
        return al.isLeveled();
    }

    
    public boolean isWorking()
    {
        return worker!=null && !worker.isDone();
    }

    private void makeNewAl() {
        if(!isWorking())
        {
            boolean guiupdate=AutoLevelSystem.leveled();
            
            al= new AutoLevelSystem(axes[0][0].getdsave(), axes[1][0].getdsave(), axes[0][1].getdsave(), axes[1][1].getdsave());
            AutoLevelSystem.publish(null);
            
            if(guiupdate)
                fireupdateGUI();
            else
                painter.trigger();
        }
    }
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jTFStartX = new javax.swing.JTextField();
        jTFStartY = new javax.swing.JTextField();
        jTFEndX = new javax.swing.JTextField();
        jTFEndY = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        jPBar = new javax.swing.JProgressBar();
        jBAction = new javax.swing.JButton();
        jBPause = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jPPaint = new cnc.gcode.controller.JPPaintable();

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Positions"));

        jLabel1.setText("X");

        jLabel2.setText("Y");

        jLabel3.setText("Start");

        jLabel4.setText("End");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING))
                .addGap(18, 18, 18)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel3)
                    .addComponent(jTFStartX, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTFStartY, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel4)
                    .addComponent(jTFEndX, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTFEndY, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTFStartX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1)
                    .addComponent(jTFEndX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTFStartY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTFEndY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Process"));

        jPBar.setString("");
        jPBar.setStringPainted(true);

        jBAction.setText("Start");
        jBAction.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jBActionActionPerformed(evt);
            }
        });

        jBPause.setText("Pause");
        jBPause.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jBPauseActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPBar, javax.swing.GroupLayout.DEFAULT_SIZE, 258, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jBAction)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jBPause)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jPBar, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jBAction)
                    .addComponent(jBPause))
                .addGap(0, 17, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Perview"));

        jPPaint.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jPPaintMouseClicked(evt);
            }
        });
        jPPaint.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                jPPaintComponentResized(evt);
            }
        });

        javax.swing.GroupLayout jPPaintLayout = new javax.swing.GroupLayout(jPPaint);
        jPPaint.setLayout(jPPaintLayout);
        jPPaintLayout.setHorizontalGroup(
            jPPaintLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPPaintLayout.setVerticalGroup(
            jPPaintLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 129, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPPaint, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPPaint, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jPPaintComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_jPPaintComponentResized
        painter.trigger();
    }//GEN-LAST:event_jPPaintComponentResized

    private void jPPaintMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jPPaintMouseClicked
        Point2D pos= trans.transform(new Point2D.Double(evt.getX(), evt.getY()), null);
        if(al.isLeveled())
        {
            JOptionPane.showMessageDialog(this, Tools.dtostr(al.getdZ(pos)));
        }
        else
        {
            AutoLevelSystem.Point sp=null;
            double d=Double.MAX_VALUE;
            for(AutoLevelSystem.Point p:al.getPoints())
                if(d>p.getPoint().distance(pos))
                {
                    sp=p;
                    d=p.getPoint().distance(pos);
                }
            if(sp!=null)
                JOptionPane.showMessageDialog(this, sp.toString());
        }
    }//GEN-LAST:event_jPPaintMouseClicked

    private void jBActionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jBActionActionPerformed
        if(AutoLevelSystem.leveled())
        {
            makeNewAl();
        }
        else if(isWorking())
        {
            worker.cancel();
            fireupdateGUI();
        }
        else
        {
            worker=new PMySwingWorker<String,Object>() {

                private boolean waitfornexdSend() throws Exception {
                    //Set Pos back
                    while (Communication.getInstance().isbussy()) {
                        if (this.isCancelled()) {
                            throw new Exception();
                        }

                        Thread.sleep(1);
                        dopause();
                    }
                    return false;
                }
                

                @Override
                protected String doInBackground() throws Exception {
                    //calc commands:
                    final AutoLevelSystem al=JPanelAutoLevel.this.al;
                    if(al.getPoints().length==0)
                    {
                        throw new MyException("No Points to level");
                    }
                    //Marlin makes an error (looks like rosunding problem with G92 stepcound it much more resulution ...)
                    //so proping 1 point two times
                    AutoLevelSystem.Point[] points=(new ArrayList<AutoLevelSystem.Point>(){
                        {
                            AutoLevelSystem.Point[] ps=al.getPoints();
                            addAll(Arrays.asList(ps));
                            add(new AutoLevelSystem.Point(ps[0].getPoint().x, ps[0].getPoint().y));
                        }
                    }).toArray(new AutoLevelSystem.Point[0]);
                    
                    
                    progress(0, "Calc Commands");
                    Thread.sleep(1000);
                    
                    //--------------Generate GCODES------------------------
                    Integer[] cmdpropeindex= new Integer[points.length];
                    ArrayList<CNCCommand> cmds= new ArrayList<>(points.length*2+20);
                    
                    //add start Command
                    cmds.add(CNCCommand.getALStartCommand());
                    
                    //go to save hight
                    cmds.add(new CNCCommand("G0 Z"+Database.ALSAVEHEIGHT));

                    AutoLevelSystem.Point aktpoint=points[0];
                    Point2D lastpos=null;
                    
                    while(true)
                    {
                        if(this.isCancelled())
                            return null;
                        
                        //go to Point
                        if(lastpos==null || !lastpos.equals(aktpoint.getPoint()))
                            cmds.add(new CNCCommand("G0 X"+Tools.dtostr(aktpoint.getPoint().getX())+" Y"+Tools.dtostr(aktpoint.getPoint().getY())));
                        lastpos=aktpoint.getPoint();
                        
                        //Prope
                        cmdpropeindex[Arrays.asList(points).indexOf(aktpoint)]=cmds.size();
                        cmds.add(new CNCCommand("G1 Z"+Tools.dtostr(Database.ALZERO.getsaved()- Database.ALMAXPROPDEPTH.getsaved())+" F"+Database.ALFEEDRATE));
                        
                        // --> Set Position + clearance is made after Propping
                        
                        //Get next nearest Point:
                        double d= Double.MAX_VALUE;
                        AutoLevelSystem.Point newpoint=null;
                        for(int i=0;i<(points.length-1);i++)
                        {
                            if(cmdpropeindex[i]==null && aktpoint.getPoint().distance(points[i].getPoint())<d)
                            {
                                newpoint=points[i];
                                d=aktpoint.getPoint().distance(points[i].getPoint());
                            }
                        }
                        if(cmdpropeindex[points.length-1]==null && newpoint==null)
                                newpoint=points[points.length-1];
                        if (newpoint==null)
                            break;
                        aktpoint=newpoint;
                    }
                    
                    //go to save hight
                    cmds.add(new CNCCommand("G0 Z"+Database.ALSAVEHEIGHT));
                    
                    //calc time
                    CNCCommand.Calchelper c= new CNCCommand.Calchelper();
                    for(int i=0;i<cmds.size();i++)
                    {
                        if(this.isCancelled())
                            return null;

                        cmds.get(i).calcCommand(c);
                        
                        //secound command go to save hight so no x and y are known => waring can be ignored! 
                        if((cmds.get(i).getState()==CNCCommand.State.ERROR || cmds.get(i).getState()==CNCCommand.State.WARNING) && i!=1 ) 
                            throw new MyException("Should not happen :-(");
                        
                        //Simulate Clearancemove
                        if(Arrays.asList(cmdpropeindex).contains(i))
                            (new CNCCommand("G0 Z"+Tools.dtostr(Database.ALZERO.getsaved()- Database.ALMAXPROPDEPTH.getsaved()+Database.ALCLEARENCE.getsaved()))).calcCommand(c);
                        
                    }
                    
                    long maxTime= (long)c.secounds;
                    
                    //Execute the Commands
                    progress(0, Tools.formatDuration(maxTime));

                    for(int i=0;i<cmds.size();i++)
                    {
                        CNCCommand cmd=cmds.get(i);

                        setProgress(100*i/cmds.size(), "~"+Tools.formatDuration(maxTime-cmd.getSecounds()));

                        for(String execute:cmd.execute(new CNCCommand.Transform(0, 0, false, false),false))
                        {
                            waitfornexdSend();
                            Communication.getInstance().send(execute);

                        }
                        
                        if(Arrays.asList(cmdpropeindex).contains(i))
                        {
                            //Proping Done waiting for hit:
                            hit=false;
                            waitfortrigger(1000*60*10);
                            
                            if(hit==false)
                                throw new MyException("Timeout: No End Stop Hit!");

                            
                            //Reset Z position
                            waitfornexdSend();
                            Communication.getInstance().send("G92 Z"+Tools.dtostr(hitvalue));

                            //Clearence
                            waitfornexdSend();
                            Communication.getInstance().send("G0 Z"+Tools.dtostr(hitvalue+Database.ALCLEARENCE.getsaved())+" F"+Database.GOFEEDRATE);

                            //Save pos
                            points[Arrays.asList(cmdpropeindex).indexOf(i)].setValue(hitvalue);
                            publish(null);                            

                        }
                    
                    }
                    
                    double error=points[0].getValue()-points[points.length-1].getValue();
                    double max=-Double.MAX_VALUE;
                    double min= Double.MAX_VALUE;
                    double sum=0;
                    
                    if(points.length>2)
                    {
                    //error correction reconstruct oder
                        Integer[] keys=Arrays.copyOf(cmdpropeindex, cmdpropeindex.length); 
                        Arrays.sort(keys,0,keys.length); 
                        for(int i=0;i<points.length-1;i++)
                            points[Arrays.asList(cmdpropeindex).indexOf(keys[i])].setValue(points[i].getValue()+i*(error/(points.length-2)));
                    }

                    for(int i=0;i<points.length-1;i++)
                    {

                        if(min>points[i].getValue())
                            min=points[i].getValue();
                        if(max<points[i].getValue())
                            max=points[i].getValue();
                        sum+=points[i].getValue();
    
                    }
                    
                    String message="Autoleveling Done!"
                            +"\n    average: "+Tools.dtostr(sum/points.length)
                            +"\n    max: "+Tools.dtostr(max)
                            +"\n    min: "+Tools.dtostr(min)
                            +"\n    error: "+Tools.dtostr(error);
                    
                    return message;
                }

                @Override
                protected void process(List chunks) {
                    painter.trigger();
                }
                
                
                @Override
                protected void done(String rvalue, Exception ex, boolean canceled) {
                    String message=rvalue;

                    if(canceled)
                    {
                        message="Canceled!";
                    }
                    else if(ex!=null)
                    {
                        message="Error: "+ex.toString();
                        ex.printStackTrace();
                    }
                    
                    JOptionPane.showMessageDialog(JPanelAutoLevel.this, message);
                    AutoLevelSystem.publish(al);

                    fireupdateGUI();
                }

            };
            worker.execute();

            fireupdateGUI();
        }
    }//GEN-LAST:event_jBActionActionPerformed

    private void jBPauseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jBPauseActionPerformed
        if(worker!=null)
            worker.pause(!worker.isPuased());

        fireupdateGUI();
    }//GEN-LAST:event_jBPauseActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jBAction;
    private javax.swing.JButton jBPause;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JProgressBar jPBar;
    private cnc.gcode.controller.JPPaintable jPPaint;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JTextField jTFEndX;
    private javax.swing.JTextField jTFEndY;
    private javax.swing.JTextField jTFStartX;
    private javax.swing.JTextField jTFStartY;
    // End of variables declaration//GEN-END:variables
}
