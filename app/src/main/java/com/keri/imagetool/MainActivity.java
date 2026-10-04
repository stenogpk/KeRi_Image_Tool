package com.keri.imagetool;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private Uri selectedUri, savedUri;
    private Bitmap selectedBitmap;
    private TextView fileInfo, resultInfo, preview;
    private EditText targetInput;
    private Spinner unitSpinner;
    private Button compressButton, shareButton;
    private byte[] compressed;
    private final int ink = Color.rgb(31, 29, 61);
    private final int purple = Color.rgb(99, 91, 255);
    private final DecimalFormat fmt = new DecimalFormat("0.##");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(ink);
        getWindow().setNavigationBarColor(ink);
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
        root.setBackgroundColor(Color.rgb(246,247,255));\n        root.setOnApplyWindowInsetsListener((v, insets) -> {\n            v.setPadding(dp(20), dp(18) + insets.getSystemWindowInsetTop(), dp(20), dp(24) + insets.getSystemWindowInsetBottom());\n            return insets;\n        });\n        scroll.addView(root);
        LinearLayout header = new LinearLayout(this); header.setOrientation(1);
        TextView brand = text("KERI  ✦  IMAGE TOOL", 13, purple, true);
        TextView title = text("Compress images", 29, ink, true); title.setPadding(0,dp(7),0,dp(4));
        TextView sub = text("Smaller file. Beautiful quality.", 14, Color.rgb(103,105,129), false);
        header.addView(brand); header.addView(title); header.addView(sub); root.addView(header);
        LinearLayout card = new LinearLayout(this); card.setOrientation(1); card.setPadding(dp(16),dp(16),dp(16),dp(16));
        card.setBackground(shape(Color.WHITE,22)); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(22); root.addView(card,cp);
        preview = text("✦\n\nChoose a photo to get started",16,Color.rgb(130,130,155),true);
        preview.setGravity(Gravity.CENTER); preview.setBackground(shape(Color.rgb(242,241,255),16));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,dp(210)); card.addView(preview,pp);
        Button choose = new Button(this); choose.setText("＋  Choose photo"); choose.setAllCaps(false);
        choose.setTextColor(Color.WHITE); choose.setBackground(shape(purple,14));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1,dp(52)); bp.topMargin=dp(14); card.addView(choose,bp);
        choose.setOnClickListener(v -> pickImage());
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
        compressButton.setOnClickListener(v -> compressImage());
        LinearLayout resultCard = new LinearLayout(this); resultCard.setOrientation(1); resultCard.setPadding(dp(16),dp(16),dp(16),dp(16)); resultCard.setBackground(shape(Color.WHITE,22));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2); rp.topMargin=dp(14); root.addView(resultCard,rp);
        resultCard.addView(text("YOUR RESULT",12,purple,true));
        resultInfo = text("Your compressed image details will appear here.",14,Color.rgb(103,105,129),false); resultInfo.setPadding(0,dp(10),0,dp(10)); resultCard.addView(resultInfo);
        shareButton = new Button(this); shareButton.setText("Save & share image"); shareButton.setAllCaps(false); shareButton.setTextColor(Color.WHITE); shareButton.setBackground(shape(Color.rgb(16,166,126),14)); shareButton.setEnabled(false); resultCard.addView(shareButton,new LinearLayout.LayoutParams(-1,dp(50)));
        shareButton.setOnClickListener(v -> saveAndShare());
        TextView footer=text("Developed by Shartendu",12,Color.rgb(120,120,145),false); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(24),0,dp(4)); root.addView(footer);
        setContentView(scroll);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_IMAGE);
    }
    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(req==PICK_IMAGE && res==RESULT_OK && data!=null && data.getData()!=null) {
            selectedUri=data.getData();
            try {
                getContentResolver().takePersistableUriPermission(selectedUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                InputStream in=getContentResolver().openInputStream(selectedUri); selectedBitmap=BitmapFactory.decodeStream(in); if(in!=null)in.close();
                if(selectedBitmap==null) throw new Exception("Unsupported image");
                compressed=null; shareButton.setEnabled(false);
                preview.setText("✓  Photo selected\n"+selectedBitmap.getWidth()+" × "+selectedBitmap.getHeight());
                preview.setTextColor(purple);
                fileInfo.setText("Original size: "+pretty(sizeOfUri(selectedUri)));
                resultInfo.setText("Ready to compress. Original photo stays untouched.");
            } catch(Exception e) { Toast.makeText(this,"Could not open this image",Toast.LENGTH_LONG).show(); }
        }
    }
    private long sizeOfUri(Uri uri) {
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)) {
            if(c!=null && c.moveToFirst()) return c.getLong(0);
        } catch(Exception ignored){} return 0;
    }
    private String pretty(long n) { return n<1024 ? n+" B" : n<1048576 ? fmt.format(n/1024.0)+" KB" : fmt.format(n/1048576.0)+" MB"; }
    private void compressImage() {
        if(selectedBitmap==null) { Toast.makeText(this,"Choose a photo first",Toast.LENGTH_SHORT).show(); return; }
        try {
            double amount=Double.parseDouble(targetInput.getText().toString().trim());
            if(amount<=0) throw new NumberFormatException();
            long target=(long)(amount*(unitSpinner.getSelectedItemPosition()==0?1024:1048576));
            if(target<1024) target=1024;
            compressButton.setEnabled(false); compressButton.setText("Compressing…");
            new Thread(() -> {
                try {
                    byte[] out=smartCompress(selectedBitmap,target);
                    runOnUiThread(() -> {
                        compressed=out; compressButton.setEnabled(true); compressButton.setText("Compress image  →"); shareButton.setEnabled(true);
                        long actual=out.length; double pct=100.0*actual/target;
                        resultInfo.setText("Compressed size: "+pretty(actual)+"\nTarget: "+pretty(target)+"  •  "+fmt.format(pct)+"% of target\nQuality-first smart compression. Original remains unchanged.");
                        if(actual>target) Toast.makeText(this,"Closest achievable size for this image",Toast.LENGTH_LONG).show();
                    });
                } catch(Exception e) { runOnUiThread(() -> { compressButton.setEnabled(true); compressButton.setText("Compress image  →"); Toast.makeText(this,"Compression failed. Try another photo.",Toast.LENGTH_LONG).show(); }); }
            }).start();
        } catch(Exception e) { targetInput.setError("Enter a valid size"); }
    }
    private byte[] encode(Bitmap b,int quality) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG,quality,out); return out.toByteArray();
    }
    private byte[] smartCompress(Bitmap source,long target) {
        Bitmap working=source; boolean scaled=false; byte[] best=encode(working,35);
        for(int pass=0;pass<14;pass++) {
            int lo=35,hi=100; byte[] passBest=null;
            while(lo<=hi) {
                int mid=(lo+hi)/2; byte[] candidate=encode(working,mid);
                if(candidate.length<=target) { passBest=candidate; lo=mid+1; }
                else hi=mid-1;
            }
            if(passBest!=null) return passBest;
            byte[] low=encode(working,35); if(low.length<best.length || pass==0) best=low;
            if(working.getWidth()<500 || working.getHeight()<500) break;
            int nw=Math.max(1,(int)(working.getWidth()*0.90)); int nh=Math.max(1,(int)(working.getHeight()*0.90));
            Bitmap smaller=Bitmap.createScaledBitmap(working,nw,nh,true);
            if(working!=source) working.recycle(); working=smaller; scaled=true;
        }
        return best;
    }
    private void saveAndShare() {
        if(compressed==null)return;
        try {
            String name="Keri_"+System.currentTimeMillis()+".jpg";
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
