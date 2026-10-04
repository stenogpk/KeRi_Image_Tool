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
    private final ArrayList<Uri> bulkUris = new ArrayList<>();
    private boolean bulkMode = false;
    private File compressedZip;
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
        TextView footer=text("Developed by Shartendu",12,Color.rgb(120,120,145),false); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(24),0,dp(4)); root.addView(footer);
        setContentView(scroll);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_IMAGE);
    }
    private void pickBulkImages() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(i,PICK_BULK);
    }
    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK || data==null) return;
        if(req==PICK_BULK) {
            bulkUris.clear();
            ClipData clip=data.getClipData();
            if(clip!=null) {
                int count=Math.min(clip.getItemCount(),50);
                for(int i=0;i<count;i++) bulkUris.add(clip.getItemAt(i).getUri());
                if(clip.getItemCount()>50) Toast.makeText(this,"Maximum 50 photos allowed. First 50 selected.",Toast.LENGTH_LONG).show();
            } else if(data.getData()!=null) bulkUris.add(data.getData());
            if(bulkUris.isEmpty()) return;
            bulkMode=true; selectedBitmap=null; selectedUri=null; compressed=null; compressedZip=null;
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
            bulkMode=false; bulkUris.clear(); compressedZip=null; selectedUri=data.getData();
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
            final long target=Math.max(1024L,(long)(amount*(unitSpinner.getSelectedItemPosition()==0?1024:1048576)));
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
            double amount=Double.parseDouble(targetInput.getText().toString().trim());
            if(amount<=0) throw new NumberFormatException();
            target=Math.max(1024L,(long)(amount*(unitSpinner.getSelectedItemPosition()==0?1024:1048576)));
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
                    resultInfo.setText("Bulk compression complete\n"+workList.size()+" photos packed into ZIP\nEach image name is prefixed with KeRi_. Ready to save and share.");
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
