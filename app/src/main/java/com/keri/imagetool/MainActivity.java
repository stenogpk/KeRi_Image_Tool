package com.keri.imagetool;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowInsets;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.text.DecimalFormat;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private static final int PICK_BULK = 42;
    private static final int PICK_RESIZE = 43;
    private final ArrayList<Uri> bulkUris = new ArrayList<>();
    private boolean bulkMode = false;
    private File compressedZip;
    private Uri resizeUri;
    private Bitmap resizeSourceBitmap, resizedBitmap;
    private int resizeOriginalWidth, resizeOriginalHeight;
    private EditText resizeWidthInput, resizeHeightInput, resizeDpiInput;
    private Spinner resizeUnitSpinner;
    private CheckBox maintainRatio;
    private ImageView resizePreview;
    private TextView resizeInfo;
    private Button resizeActionButton, resizeSaveButton;
    private boolean updatingResizeFields=false;
    private int lastResizeUnit=0;
    private double lastResizeDpi=300;
    private byte[] resizedJpeg;
    private Uri selectedUri, savedUri;
    private Bitmap selectedBitmap;
    private TextView fileInfo, resultInfo, previewLabel;
    private ImageView previewImage;
    private EditText targetInput;
    private Spinner unitSpinner;
    private Button compressButton, shareButton;
    private byte[] compressed;
    private final int ink = Color.rgb(31, 29, 61);
    private final int purple = Color.rgb(99, 91, 255);
    private final DecimalFormat fmt = new DecimalFormat("0.##");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }
        buildUi();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(246,247,255));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = 0, bottomInset = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });
        scroll.addView(root);
        LinearLayout header = new LinearLayout(this); header.setOrientation(1);
        TextView brand = text("KERI  ✦  IMAGE TOOL", 13, purple, true);
        TextView title = text("Compress images", 29, ink, true); title.setPadding(0,dp(7),0,dp(4));
        TextView sub = text("Smaller file. Beautiful quality.", 14, Color.rgb(103,105,129), false);
        header.addView(brand); header.addView(title); header.addView(sub); root.addView(header);
        LinearLayout card = new LinearLayout(this); card.setOrientation(1); card.setPadding(dp(16),dp(16),dp(16),dp(16));
        card.setBackground(shape(Color.WHITE,22)); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(22); root.addView(card,cp);
        LinearLayout previewBox = new LinearLayout(this); previewBox.setOrientation(1);
        previewBox.setGravity(Gravity.CENTER); previewBox.setPadding(dp(12),dp(10),dp(12),dp(10));
        previewBox.setBackground(shape(Color.rgb(242,241,255),16));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,dp(210)); card.addView(previewBox,pp);
        previewImage = new ImageView(this); previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setVisibility(View.GONE);
        previewBox.addView(previewImage,new LinearLayout.LayoutParams(dp(132),dp(132)));
        previewLabel = text("✦\\nChoose a photo to get started",16,Color.rgb(130,130,155),true);
        previewLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1,-2); plp.topMargin=dp(4);
        previewBox.addView(previewLabel,plp);
        Button choose = new Button(this); choose.setText("＋  Choose photo"); choose.setAllCaps(false);
        choose.setTextColor(Color.WHITE); choose.setBackground(shape(purple,14));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1,dp(52)); bp.topMargin=dp(14); card.addView(choose,bp);
        choose.setOnClickListener(v -> pickImage());
        Button chooseBulk = new Button(this); chooseBulk.setText("＋  Choose up to 50 photos (Bulk)");
        chooseBulk.setAllCaps(false); chooseBulk.setTextColor(purple); chooseBulk.setBackground(shape(Color.rgb(239,238,255),14));
        LinearLayout.LayoutParams bulkPickParams = new LinearLayout.LayoutParams(-1,dp(48)); bulkPickParams.topMargin=dp(9); card.addView(chooseBulk,bulkPickParams);
        chooseBulk.setOnClickListener(v -> pickBulkImages());
        fileInfo = text("No image selected",13,Color.GRAY,false); fileInfo.setPadding(0,dp(10),0,0); card.addView(fileInfo);
        LinearLayout targetCard = new LinearLayout(this); targetCard.setOrientation(1); targetCard.setPadding(dp(16),dp(16),dp(16),dp(16)); targetCard.setBackground(shape(Color.WHITE,22));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(14); root.addView(targetCard,tp);
        targetCard.addView(text("TARGET FILE SIZE",12,purple,true));
        TextView hint = text("Set a size — we’ll get as close as possible",13,Color.rgb(103,105,129),false); hint.setPadding(0,dp(5),0,dp(12)); targetCard.addView(hint);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); targetCard.addView(row);
        targetInput = new EditText(this); targetInput.setSingleLine(true); targetInput.setText("100"); targetInput.setTextSize(25); targetInput.setTextColor(ink); targetInput.setInputType(2); targetInput.setPadding(dp(12),0,dp(12),0); targetInput.setBackground(shape(Color.rgb(246,247,255),12));
        row.addView(targetInput,new LinearLayout.LayoutParams(0,dp(54),1));
        unitSpinner = new Spinner(this); String[] units={"KB","MB"}; ArrayAdapter<String> ad = new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,units); unitSpinner.setAdapter(ad);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(95),dp(54)); sp.leftMargin=dp(10); row.addView(unitSpinner,sp);
        compressButton = new Button(this); compressButton.setText("Compress image  →"); compressButton.setAllCaps(false); compressButton.setTextColor(Color.WHITE); compressButton.setTextSize(16); compressButton.setTypeface(null,Typeface.BOLD); compressButton.setBackground(shape(ink,14));
        LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(-1,dp(54)); cb.topMargin=dp(16); targetCard.addView(compressButton,cb);
        compressButton.setOnClickListener(v -> { if (bulkMode) compressBulkImages(); else compressImage(); });
        LinearLayout resultCard = new LinearLayout(this); resultCard.setOrientation(1); resultCard.setPadding(dp(16),dp(16),dp(16),dp(16)); resultCard.setBackground(shape(Color.WHITE,22));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2); rp.topMargin=dp(14); root.addView(resultCard,rp);
        resultCard.addView(text("YOUR RESULT",12,purple,true));
        resultInfo = text("Your compressed image details will appear here.",14,Color.rgb(103,105,129),false); resultInfo.setPadding(0,dp(10),0,dp(10)); resultCard.addView(resultInfo);
        shareButton = new Button(this); shareButton.setText("Save & share image"); shareButton.setAllCaps(false); shareButton.setTextColor(Color.WHITE); shareButton.setBackground(shape(Color.rgb(16,166,126),14)); shareButton.setEnabled(false); resultCard.addView(shareButton,new LinearLayout.LayoutParams(-1,dp(50)));
        shareButton.setOnClickListener(v -> { if (compressedZip != null) saveAndShareZip(); else saveAndShare(); });
        LinearLayout resizeCard = new LinearLayout(this); resizeCard.setOrientation(1); resizeCard.setPadding(dp(16),dp(16),dp(16),dp(16)); resizeCard.setBackground(shape(Color.WHITE,22));
        LinearLayout.LayoutParams resizeCardParams = new LinearLayout.LayoutParams(-1,-2); resizeCardParams.topMargin=dp(14); root.addView(resizeCard,resizeCardParams);
        resizeCard.addView(text("IMAGE RESIZER",12,purple,true));
        TextView resizeHint=text("Change image dimensions in pixels or physical units.",13,Color.rgb(103,105,129),false); resizeHint.setPadding(0,dp(5),0,dp(12)); resizeCard.addView(resizeHint);
        Button resizeChooseButton=new Button(this); resizeChooseButton.setText("＋  Choose image to resize"); resizeChooseButton.setAllCaps(false); resizeChooseButton.setTextColor(Color.WHITE); resizeChooseButton.setBackground(shape(purple,14)); resizeCard.addView(resizeChooseButton,new LinearLayout.LayoutParams(-1,dp(48)));
        resizeChooseButton.setOnClickListener(v -> pickResizeImage());
        resizePreview=new ImageView(this); resizePreview.setScaleType(ImageView.ScaleType.FIT_CENTER); resizePreview.setVisibility(View.GONE);
        LinearLayout.LayoutParams resizePreviewParams=new LinearLayout.LayoutParams(-1,dp(150)); resizePreviewParams.topMargin=dp(10); resizeCard.addView(resizePreview,resizePreviewParams);
        resizeInfo=text("No image selected",12,Color.GRAY,false); resizeInfo.setPadding(0,dp(8),0,dp(10)); resizeCard.addView(resizeInfo);
        LinearLayout resizeUnitRow=new LinearLayout(this); resizeUnitRow.setGravity(Gravity.CENTER_VERTICAL); resizeCard.addView(resizeUnitRow);
        resizeUnitSpinner=new Spinner(this); ArrayAdapter<String> resizeUnitsAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Pixels (px)","Inches (in)","Centimeters (cm)"}); resizeUnitSpinner.setAdapter(resizeUnitsAdapter);
        resizeUnitRow.addView(resizeUnitSpinner,new LinearLayout.LayoutParams(0,dp(48),1));
        resizeDpiInput=new EditText(this); resizeDpiInput.setSingleLine(true); resizeDpiInput.setText("300"); resizeDpiInput.setTextSize(15); resizeDpiInput.setInputType(2); resizeDpiInput.setHint("DPI"); resizeDpiInput.setTextColor(ink); resizeDpiInput.setBackground(shape(Color.rgb(246,247,255),10));
        LinearLayout.LayoutParams dpiParams=new LinearLayout.LayoutParams(dp(92),dp(48)); dpiParams.leftMargin=dp(8); resizeUnitRow.addView(resizeDpiInput,dpiParams);
        TextView dpiHint=text("DPI used for inch/cm conversion",11,Color.GRAY,false); dpiHint.setPadding(0,dp(3),0,dp(8)); resizeCard.addView(dpiHint);
        LinearLayout dimensionsRow=new LinearLayout(this); dimensionsRow.setGravity(Gravity.CENTER_VERTICAL); resizeCard.addView(dimensionsRow);
        resizeWidthInput=dimensionInput("Width"); resizeHeightInput=dimensionInput("Height");
        dimensionsRow.addView(resizeWidthInput,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView times=text("  ×  ",15,Color.GRAY,true); dimensionsRow.addView(times);
        dimensionsRow.addView(resizeHeightInput,new LinearLayout.LayoutParams(0,dp(52),1));
        maintainRatio=new CheckBox(this); maintainRatio.setText("Maintain aspect ratio"); maintainRatio.setTextColor(ink); maintainRatio.setTextSize(14); maintainRatio.setChecked(true); maintainRatio.setPadding(0,dp(8),0,dp(8)); resizeCard.addView(maintainRatio);
        resizeActionButton=new Button(this); resizeActionButton.setText("Resize image  →"); resizeActionButton.setAllCaps(false); resizeActionButton.setTextColor(Color.WHITE); resizeActionButton.setTypeface(null,Typeface.BOLD); resizeActionButton.setBackground(shape(ink,14)); resizeCard.addView(resizeActionButton,new LinearLayout.LayoutParams(-1,dp(52)));
        resizeActionButton.setOnClickListener(v -> resizeSelectedImage());
        Button saveResizedButton=new Button(this); saveResizedButton.setText("Save resized image"); saveResizedButton.setAllCaps(false); saveResizedButton.setTextColor(Color.WHITE); saveResizedButton.setBackground(shape(Color.rgb(16,166,126),14)); saveResizedButton.setEnabled(false); resizeCard.addView(saveResizedButton,new LinearLayout.LayoutParams(-1,dp(48)));
        resizeSaveButton=saveResizedButton;
        saveResizedButton.setOnClickListener(v -> saveResizedImage());
        resizeUnitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id) { convertResizeUnit(position); }
        });
        android.text.TextWatcher widthWatcher=new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x,int st,int count,int after) {}
            @Override public void onTextChanged(CharSequence x,int st,int before,int count) {
                if(!updatingResizeFields && maintainRatio.isChecked() && resizeOriginalWidth>0 && resizeOriginalHeight>0) updatePairedDimension(true);
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        };
        android.text.TextWatcher heightWatcher=new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x,int st,int count,int after) {}
            @Override public void onTextChanged(CharSequence x,int st,int before,int count) {
                if(!updatingResizeFields && maintainRatio.isChecked() && resizeOriginalWidth>0 && resizeOriginalHeight>0) updatePairedDimension(false);
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        };
        resizeWidthInput.addTextChangedListener(widthWatcher); resizeHeightInput.addTextChangedListener(heightWatcher);
        maintainRatio.setOnCheckedChangeListener((buttonView,isChecked) -> { if(isChecked) updatePairedDimension(true); });
        resizeDpiInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x,int st,int count,int after) {}
            @Override public void onTextChanged(CharSequence x,int st,int before,int count) { if(resizeOriginalWidth>0) convertResizeDpi(); }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        TextView footer=text("Developed by Shartendu",12,Color.rgb(120,120,145),false); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(24),0,dp(4)); root.addView(footer);
        setContentView(scroll);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_IMAGE);
    }
    private void pickResizeImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_RESIZE);
    }
    private void pickBulkImages() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(i,PICK_BULK);
    }
    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK || data==null) return;
        if(req==PICK_RESIZE && data.getData()!=null) {
            resizeUri=data.getData(); resizedJpeg=null; if(resizedBitmap!=null && resizedBitmap!=resizeSourceBitmap){resizedBitmap.recycle();resizedBitmap=null;} if(resizeSourceBitmap!=null){resizeSourceBitmap.recycle();resizeSourceBitmap=null;}
            try {
                try { getContentResolver().takePersistableUriPermission(resizeUri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {}
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(resizeUri)){BitmapFactory.decodeStream(in,null,bounds);}
                if(bounds.outWidth<=0 || bounds.outHeight<=0) throw new Exception("Unsupported image");
                resizeOriginalWidth=bounds.outWidth; resizeOriginalHeight=bounds.outHeight;
                int sample=1; while(Math.max(bounds.outWidth,bounds.outHeight)/sample>1200) sample*=2;
                BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=sample; opts.inPreferredConfig=Bitmap.Config.RGB_565;
                try(InputStream in=getContentResolver().openInputStream(resizeUri)){resizeSourceBitmap=BitmapFactory.decodeStream(in,null,opts);}
                if(resizeSourceBitmap==null) throw new Exception("Cannot decode image");
                resizePreview.setImageBitmap(resizeSourceBitmap); resizePreview.setVisibility(View.VISIBLE);
                resizeInfo.setText("Original: "+resizeOriginalWidth+" × "+resizeOriginalHeight+" px  •  "+pretty(sizeOfUri(resizeUri)));
                updatingResizeFields=true; resizeUnitSpinner.setSelection(0); lastResizeUnit=0; lastResizeDpi=currentDpi(); resizeWidthInput.setText(String.valueOf(resizeOriginalWidth)); resizeHeightInput.setText(String.valueOf(resizeOriginalHeight)); updatingResizeFields=false;
                resizeSaveButton.setEnabled(false);
            } catch(Exception e) { resizeInfo.setText("Could not open this image. Please choose another photo."); Toast.makeText(this,"Could not open image",Toast.LENGTH_LONG).show(); }
            return;
        }
        if(req==PICK_BULK) {
            bulkUris.clear();
            ClipData clip=data.getClipData();
            if(clip!=null) {
                int count=Math.min(clip.getItemCount(),50);
                for(int i=0;i<count;i++) { Uri itemUri=clip.getItemAt(i).getUri(); bulkUris.add(itemUri); try { getContentResolver().takePersistableUriPermission(itemUri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {} }
                if(clip.getItemCount()>50) Toast.makeText(this,"Maximum 50 photos allowed. First 50 selected.",Toast.LENGTH_LONG).show();
            } else if(data.getData()!=null) bulkUris.add(data.getData());
            if(bulkUris.isEmpty()) return;
            bulkMode=true; if(selectedBitmap!=null){selectedBitmap.recycle();selectedBitmap=null;} selectedUri=null; compressed=null; compressedZip=null;
            previewImage.setImageDrawable(null); previewImage.setVisibility(View.GONE);
            previewLabel.setText("✓  Bulk selection ready\n"+bulkUris.size()+" photos selected");
            previewLabel.setTextColor(purple); previewLabel.setTextSize(15);
            fileInfo.setText(bulkUris.size()+" photos selected • Bulk ZIP output");
            compressButton.setText("Compress "+bulkUris.size()+" photos  →");
            shareButton.setEnabled(false);
            resultInfo.setText("Ready for bulk compression. Photos will be processed one by one to reduce memory use.");
            return;
        }
        if(req==PICK_IMAGE && data.getData()!=null) {
            bulkMode=false; bulkUris.clear(); compressedZip=null; if(selectedBitmap!=null){selectedBitmap.recycle();selectedBitmap=null;} selectedUri=data.getData();
            compressButton.setText("Compress image  →");
            try {
                try { getContentResolver().takePersistableUriPermission(selectedUri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {}
                InputStream in=getContentResolver().openInputStream(selectedUri); selectedBitmap=BitmapFactory.decodeStream(in); if(in!=null)in.close();
                if(selectedBitmap==null) throw new Exception("Unsupported image");
                compressed=null; shareButton.setEnabled(false);
                int thumbW = selectedBitmap.getWidth();
                int thumbH = selectedBitmap.getHeight();
                float thumbScale = Math.min(1f, 360f / Math.max(thumbW, thumbH));
                Bitmap thumbnail = Bitmap.createScaledBitmap(selectedBitmap,
                    Math.max(1, Math.round(thumbW * thumbScale)),
                    Math.max(1, Math.round(thumbH * thumbScale)), true);
                previewImage.setImageBitmap(thumbnail); previewImage.setVisibility(View.VISIBLE);
                previewLabel.setText("✓  Photo selected  •  " + thumbW + " × " + thumbH);
                previewLabel.setTextColor(purple); previewLabel.setTextSize(13);
                fileInfo.setText("Original size: "+pretty(sizeOfUri(selectedUri)));
                resultInfo.setText("Ready to compress. Original photo stays untouched.");
            } catch(Exception e) { Toast.makeText(this,"Could not open this image",Toast.LENGTH_LONG).show(); }
        }
    }
    private EditText dimensionInput(String hint) {
        EditText input=new EditText(this); input.setSingleLine(true); input.setTextSize(20); input.setInputType(8194); input.setHint(hint); input.setTextColor(ink); input.setPadding(dp(12),0,dp(12),0); input.setBackground(shape(Color.rgb(246,247,255),10)); return input;
    }
    private double currentDpi() { try { double d=Double.parseDouble(resizeDpiInput.getText().toString().trim()); return d>0&&d<=2400?d:300; } catch(Exception e){return 300;} }
    private double toPixels(double value) {
        int unit=resizeUnitSpinner.getSelectedItemPosition();
        if(unit==1) return value*currentDpi();
        if(unit==2) return value*currentDpi()/2.54;
        return value;
    }
    private double fromPixels(double px) {
        int unit=resizeUnitSpinner.getSelectedItemPosition();
        if(unit==1) return px/currentDpi();
        if(unit==2) return px*2.54/currentDpi();
        return px;
    }
    private double pixelsForUnit(double value,int unit,double dpi) {
        if(unit==1) return value*dpi;
        if(unit==2) return value*dpi/2.54;
        return value;
    }
    private void convertResizeUnit(int newUnit) {
        if(resizeWidthInput==null)return;
        if(resizeOriginalWidth<=0) { lastResizeUnit=newUnit; lastResizeDpi=currentDpi(); return; }
        try {
            double w=Double.parseDouble(resizeWidthInput.getText().toString().trim());
            double h=Double.parseDouble(resizeHeightInput.getText().toString().trim());
            double wPx=pixelsForUnit(w,lastResizeUnit,lastResizeDpi), hPx=pixelsForUnit(h,lastResizeUnit,lastResizeDpi);
            updatingResizeFields=true;
            resizeWidthInput.setText(formatDimension(fromPixels(wPx)));
            resizeHeightInput.setText(formatDimension(fromPixels(hPx)));
            lastResizeUnit=newUnit; lastResizeDpi=currentDpi();
        } catch(Exception ignored) { lastResizeUnit=newUnit; lastResizeDpi=currentDpi(); }
        finally { updatingResizeFields=false; }
    }
    private void convertResizeDpi() {
        if(resizeWidthInput==null || lastResizeUnit==0) { lastResizeDpi=currentDpi(); return; }
        try {
            double w=Double.parseDouble(resizeWidthInput.getText().toString().trim());
            double h=Double.parseDouble(resizeHeightInput.getText().toString().trim());
            double wPx=pixelsForUnit(w,lastResizeUnit,lastResizeDpi), hPx=pixelsForUnit(h,lastResizeUnit,lastResizeDpi);
            lastResizeDpi=currentDpi();
            updatingResizeFields=true;
            resizeWidthInput.setText(formatDimension(fromPixels(wPx)));
            resizeHeightInput.setText(formatDimension(fromPixels(hPx)));
        } catch(Exception ignored) { lastResizeDpi=currentDpi(); }
        finally { updatingResizeFields=false; }
    }
    private String formatDimension(double value) { return resizeUnitSpinner.getSelectedItemPosition()==0?String.valueOf(Math.round(value)):fmt.format(value); }
    private void updatePairedDimension(boolean widthChanged) {
        try {
            EditText source=widthChanged?resizeWidthInput:resizeHeightInput;
            double entered=Double.parseDouble(source.getText().toString().trim());
            double px=toPixels(entered);
            if(px<=0)return;
            double paired=widthChanged?px*resizeOriginalHeight/resizeOriginalWidth:px*resizeOriginalWidth/resizeOriginalHeight;
            updatingResizeFields=true;
            (widthChanged?resizeHeightInput:resizeWidthInput).setText(formatDimension(fromPixels(paired)));
            updatingResizeFields=false;
        } catch(Exception ignored) { updatingResizeFields=false; }
    }
    private void resizeSelectedImage() {
        if(resizeUri==null){Toast.makeText(this,"Choose an image first",Toast.LENGTH_SHORT).show();return;}
        final int targetW,targetH;
        try {
            double w=toPixels(Double.parseDouble(resizeWidthInput.getText().toString().trim()));
            double h=toPixels(Double.parseDouble(resizeHeightInput.getText().toString().trim()));
            if(w<1||h<1||w>12000||h>12000) throw new IllegalArgumentException();
            targetW=(int)Math.round(w); targetH=(int)Math.round(h);
        } catch(Exception e){resizeWidthInput.setError("Enter valid dimensions (1–12000)");return;}
        resizeActionButton.setEnabled(false); resizeActionButton.setText("Resizing…"); resizeSaveButton.setEnabled(false);
        new Thread(() -> {
            Bitmap output=null;
            try {
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(resizeUri)){BitmapFactory.decodeStream(in,null,bounds);}
                int sample=1; int maxTarget=Math.max(targetW,targetH);
                while(Math.max(bounds.outWidth,bounds.outHeight)/(sample*2)>=maxTarget*1.5 && sample<32) sample*=2;
                BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=sample; opts.inPreferredConfig=Bitmap.Config.RGB_565;
                Bitmap source;
                try(InputStream in=getContentResolver().openInputStream(resizeUri)){source=BitmapFactory.decodeStream(in,null,opts);}
                if(source==null) throw new Exception("Image decode failed");
                output=Bitmap.createScaledBitmap(source,targetW,targetH,true); if(output!=source)source.recycle();
                ByteArrayOutputStream stream=new ByteArrayOutputStream(); output.compress(Bitmap.CompressFormat.JPEG,95,stream); byte[] bytes=stream.toByteArray();
                Bitmap finalOutput=output;
                runOnUiThread(() -> {
                    if(resizedBitmap!=null && resizedBitmap!=resizeSourceBitmap)resizedBitmap.recycle();
                    resizedBitmap=finalOutput; resizedJpeg=bytes; resizePreview.setImageBitmap(finalOutput);
                    resizeInfo.setText("Resized: "+targetW+" × "+targetH+" px  •  "+pretty(bytes.length)+"  •  JPEG");
                    resizeActionButton.setEnabled(true); resizeActionButton.setText("Resize image  →"); resizeSaveButton.setEnabled(true);
                });
            } catch(Exception e) {
                if(output!=null && !output.isRecycled())output.recycle();
                runOnUiThread(() -> {resizeActionButton.setEnabled(true);resizeActionButton.setText("Resize image  →");Toast.makeText(this,"Resize failed. Try smaller dimensions.",Toast.LENGTH_LONG).show();});
            }
        }).start();
    }
    private void saveResizedImage() {
        if(resizedJpeg==null)return;
        try {
            String name=makeKeRiJpegName(getDisplayName(resizeUri));
            ContentValues values=new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME,name); values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Keri Image Tool"); values.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values); if(uri==null)throw new Exception("Save failed");
            try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(resizedJpeg);}
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);
            Toast.makeText(this,"Resized image saved to Pictures/Keri Image Tool",Toast.LENGTH_LONG).show();
        } catch(Exception e){Toast.makeText(this,"Could not save resized image",Toast.LENGTH_LONG).show();}
    }
    private long sizeOfUri(Uri uri) {
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)) {
            if(c!=null && c.moveToFirst()) return c.getLong(0);
        } catch(Exception ignored){} return 0;
    }
    private String pretty(long n) { return n<1000 ? n+" B" : n<1000000 ? fmt.format(n/1000.0)+" KB" : fmt.format(n/1000000.0)+" MB"; }
    // Leave a full display-unit safety margin so file managers cannot round the result above the requested limit.
    private long safeTargetBytes(String raw,int unitPosition) {
        double amount=Double.parseDouble(raw.trim());
        if(Double.isNaN(amount)||Double.isInfinite(amount)||amount<=0) throw new IllegalArgumentException("Enter a valid target size");
        double bytes;
        if(unitPosition==0) {
            if(amount<=1.0) throw new IllegalArgumentException("For a guaranteed result, enter more than 1 KB");
            bytes=Math.floor((amount-1.0)*1000.0);
        } else {
            if(amount<=0.001) throw new IllegalArgumentException("Enter a larger target size");
            bytes=Math.floor((amount-0.001)*1000000.0);
        }
        if(bytes<1024.0) throw new IllegalArgumentException("Target is too small to guarantee a valid JPEG. Increase the size.");
        if(bytes>Long.MAX_VALUE) throw new IllegalArgumentException("Target size is too large");
        return (long)bytes;
    }
    private void compressImage() {
        if(selectedBitmap==null) { Toast.makeText(this,"Choose a photo first",Toast.LENGTH_SHORT).show(); return; }
        try {
            final long target=safeTargetBytes(targetInput.getText().toString(),unitSpinner.getSelectedItemPosition());
            compressButton.setEnabled(false); compressButton.setText("Compressing…");
            new Thread(() -> {
                try {
                    byte[] out=smartCompress(selectedBitmap,target);
                    runOnUiThread(() -> {
                        compressed=out; compressButton.setEnabled(true); compressButton.setText("Compress image  →"); shareButton.setEnabled(true);
                        long actual=out.length; double pct=100.0*actual/target;
                        resultInfo.setText("Compressed size: "+pretty(actual)+"\nMaximum allowed: "+pretty(target)+"  •  "+fmt.format(pct)+"% of limit\nStrict size limit met. Best available quality within the limit. Original remains unchanged.");
                    });
                } catch(Exception e) { runOnUiThread(() -> { compressButton.setEnabled(true); compressButton.setText("Compress image  →"); Toast.makeText(this,"Compression failed. Try another photo.",Toast.LENGTH_LONG).show(); }); }
            }).start();
        } catch(Exception e) { targetInput.setError("Enter a valid size"); }
    }
    private byte[] encode(Bitmap b,int quality) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG,quality,out); return out.toByteArray();
    }
    private byte[] smartCompress(Bitmap source,long target) {
        Bitmap working=source;
        try {
            // Preserve original dimensions first; reduce resolution only when quality 1 cannot fit.
            for(int pass=0;pass<48;pass++) {
                int lo=1,hi=100;
                byte[] bestWithinLimit=null;
                while(lo<=hi) {
                    int mid=(lo+hi)/2;
                    byte[] candidate=encode(working,mid);
                    if(candidate.length<=target) {
                        bestWithinLimit=candidate;
                        lo=mid+1;
                    } else {
                        hi=mid-1;
                    }
                }
                if(bestWithinLimit!=null && bestWithinLimit.length<=target) return bestWithinLimit;
                int width=working.getWidth(),height=working.getHeight();
                if(width<=32 || height<=32) break;
                int nextWidth=Math.max(1,Math.round(width*0.85f));
                int nextHeight=Math.max(1,Math.round(height*0.85f));
                if(nextWidth>=width) nextWidth=width-1;
                if(nextHeight>=height) nextHeight=height-1;
                Bitmap smaller=Bitmap.createScaledBitmap(working,nextWidth,nextHeight,true);
                if(working!=source) working.recycle();
                working=smaller;
            }
            throw new IllegalArgumentException("This photo cannot be reduced below the strict size limit. Increase the target size.");
        } finally {
            if(working!=source && !working.isRecycled()) working.recycle();
        }
    }
    private String getDisplayName(Uri uri) {
        if(uri==null) return "Image";
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst()) return c.getString(0);
        } catch(Exception ignored) {}
        String last=uri.getLastPathSegment();
        return last==null ? "Image" : last;
    }
    private String makeKeRiJpegName(String original) {
        if(original==null || original.trim().isEmpty()) original="Image";
        String base=original; int dot=base.lastIndexOf('.');
        if(dot>0) base=base.substring(0,dot);
        if(base.regionMatches(true,0,"KeRi_",0,5)) return base+".jpg";
        return "KeRi_"+base+".jpg";
    }
    private void compressBulkImages() {
        if(bulkUris.isEmpty()) { Toast.makeText(this,"Choose photos first",Toast.LENGTH_SHORT).show(); return; }
        final long target;
        try {
            target=safeTargetBytes(targetInput.getText().toString(),unitSpinner.getSelectedItemPosition());
        } catch(Exception e) { targetInput.setError("Enter a valid size"); return; }
        compressButton.setEnabled(false); shareButton.setEnabled(false);
        compressButton.setText("Preparing ZIP…");
        final ArrayList<Uri> workList=new ArrayList<>(bulkUris);
        new Thread(() -> {
            File zip=null;
            try {
                zip=File.createTempFile("keri_bulk_",".zip",getCacheDir());
                Set<String> usedNames=new HashSet<>();
                try(ZipOutputStream zos=new ZipOutputStream(new FileOutputStream(zip))) {
                    for(int index=0;index<workList.size();index++) {
                        Uri uri=workList.get(index);
                        Bitmap bitmap=decodeForBulk(uri);
                        if(bitmap==null) throw new Exception("Cannot decode "+getDisplayName(uri));
                        byte[] output;
                        try { output=smartCompress(bitmap,target); } finally { bitmap.recycle(); }
                        String entryName=uniqueZipName(makeKeRiJpegName(getDisplayName(uri)),usedNames);
                        zos.putNextEntry(new ZipEntry(entryName)); zos.write(output); zos.closeEntry();
                        final int done=index+1;
                        runOnUiThread(() -> compressButton.setText("Compressing "+done+"/"+workList.size()+"…"));
                    }
                }
                final File completedZip=zip;
                runOnUiThread(() -> {
                    compressedZip=completedZip; compressed=null;
                    compressButton.setEnabled(true); compressButton.setText("Compress "+workList.size()+" photos  →");
                    shareButton.setEnabled(true);
                    resultInfo.setText("Bulk compression complete\n"+workList.size()+" photos packed into ZIP\nMaximum per photo: "+pretty(target)+"\nEvery photo passed the strict size limit. Names are prefixed with KeRi_.");
                    Toast.makeText(this,"Bulk ZIP is ready",Toast.LENGTH_LONG).show();
                });
            } catch(Exception e) {
                if(zip!=null) zip.delete();
                runOnUiThread(() -> {
                    compressButton.setEnabled(true); compressButton.setText("Compress "+workList.size()+" photos  →");
                    Toast.makeText(this,"Bulk compression stopped. Try fewer or smaller photos.",Toast.LENGTH_LONG).show();
                    resultInfo.setText("Bulk compression could not finish. Your original photos are unchanged.");
                });
            }
        }).start();
    }
    private Bitmap decodeForBulk(Uri uri) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in,null,bounds); }
        int sample=1; int maxSide=Math.max(bounds.outWidth,bounds.outHeight);
        while(maxSide/sample>2400) sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=sample; opts.inPreferredConfig=Bitmap.Config.RGB_565;
        try(InputStream in=getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in,null,opts); }
    }
    private String uniqueZipName(String desired,Set<String> used) {
        String candidate=desired; int n=2;
        while(!used.add(candidate.toLowerCase(java.util.Locale.ROOT))) {
            int dot=desired.lastIndexOf('.');
            candidate=(dot>0?desired.substring(0,dot):desired)+" ("+(n++)+")"+(dot>0?desired.substring(dot):".jpg");
        }
        return candidate;
    }
    private void saveAndShareZip() {
        if(compressedZip==null || !compressedZip.exists()) return;
        try {
            String name="KeRi_Bulk_"+System.currentTimeMillis()+".zip";
            ContentValues values=new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
            if(Build.VERSION.SDK_INT>=29) values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download");
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null) throw new Exception("Cannot create ZIP");
            try(InputStream in=new java.io.FileInputStream(compressedZip); OutputStream out=getContentResolver().openOutputStream(uri)) {
                byte[] buffer=new byte[32768]; int read;
                while((read=in.read(buffer))!=-1) out.write(buffer,0,read);
            }
            Intent share=new Intent(Intent.ACTION_SEND); share.setType("application/zip");
            share.putExtra(Intent.EXTRA_STREAM,uri); share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Share compressed photos ZIP"));
            Toast.makeText(this,"ZIP saved to Downloads",Toast.LENGTH_LONG).show();
        } catch(Exception e) { Toast.makeText(this,"Could not save ZIP",Toast.LENGTH_LONG).show(); }
    }
    private void saveAndShare() {
        if(compressed==null)return;
        try {
            String name=makeKeRiJpegName(getDisplayName(selectedUri));
            ContentValues values=new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME,name); values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Keri Image Tool"); values.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new Exception("Save failed");
            try(OutputStream os=getContentResolver().openOutputStream(uri)){os.write(compressed);}
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0); getContentResolver().update(uri,values,null,null);
            savedUri=uri;
            Intent share=new Intent(Intent.ACTION_SEND); share.setType("image/jpeg"); share.putExtra(Intent.EXTRA_STREAM,uri); share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Share compressed image"));
            Toast.makeText(this,"Saved to Pictures/Keri Image Tool",Toast.LENGTH_LONG).show();
        } catch(Exception e) { Toast.makeText(this,"Could not save image",Toast.LENGTH_LONG).show(); }
    }
}
