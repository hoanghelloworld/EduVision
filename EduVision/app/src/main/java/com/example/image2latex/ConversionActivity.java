package com.example.image2latex;

import java.io.File;
import java.io.IOException;
import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.image2latex.databinding.ActivityConversionBinding;

// Import utility classes from the project
import com.example.image2latex.utils.UIHelper;
import com.example.image2latex.utils.PermissionManager;
import com.example.image2latex.utils.ImageHandler;
import com.example.image2latex.LaTeXConverter;

public class ConversionActivity extends AppCompatActivity {
    private ActivityConversionBinding binding;
    private LaTeXConverter converter;
    private Thread conversionThread = null;
    private Uri cameraImageUri = null;
    
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int CAMERA_REQUEST_CODE = 102;
    private static final int GALLERY_REQUEST_CODE = 103;
    private static final int UCROP_REQUEST_CODE = 104;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Enable up navigation
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chuyển Đổi Ảnh Sang LaTeX");
        }
        
        UIHelper.initializeUI(binding);
        initializeConverter();
        setupButtonListeners();
        setupTextWatcher();
    }
    
    private void initializeConverter() {
        try {
            // Initialize converter synchronously - no need for async initialization since
            // we're not loading large models anymore
            converter = new LaTeXConverter(this);
            UIHelper.enableButtons(binding);
        } catch (Exception e) {
            handleConverterInitError(e);
        }
    }

    private void setupButtonListeners() {
        binding.galleryButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            if (PermissionManager.checkStoragePermission(this)) {
                openGallery();
            } else {
                PermissionManager.checkPermission(this, PermissionManager.getStoragePermission(), STORAGE_PERMISSION_CODE);
            }
        });
        
        binding.cameraButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            if (PermissionManager.checkCameraPermission(this)) {
                openCamera();
            } else {
                PermissionManager.checkPermission(this, Manifest.permission.CAMERA, CAMERA_PERMISSION_CODE);
            }
        });
        
        binding.cancelButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            if (converter != null && conversionThread != null && conversionThread.isAlive()) {
                converter.cancelConversion();
                Toast.makeText(this, "Đang hủy...", Toast.LENGTH_SHORT).show();
            }
        });
        
        binding.previewButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            UIHelper.showPreview(this, binding.resultText.getText().toString());
        });
        
        binding.copyButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            String latexCode = binding.resultText.getText().toString();
            if (!latexCode.isEmpty()) {
                UIHelper.copyToClipboard(this, latexCode);
            }
        });

        binding.pasteButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            UIHelper.pasteFromClipboard(this, binding);
        });
    }
    
    private void setupTextWatcher() {
        binding.resultText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                UIHelper.updateButtonVisibility(binding, s.length() > 0);
            }
        });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }

    private void openCamera() {
        try {
            File photoFile = UIHelper.createImageFile(this);
            cameraImageUri = UIHelper.getUriForFile(this, photoFile);
            startActivityForResult(UIHelper.createCameraIntent(this, cameraImageUri), 
                CAMERA_REQUEST_CODE);
        } catch (IOException e) {
            Toast.makeText(this, "Lỗi khi tạo tệp ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImage(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.imageView.setImageBitmap(bitmap);
        binding.resultText.setText("Đang xử lý ảnh...");
        UIHelper.updateUIState(binding, false);
        
        conversionThread = new Thread(() -> {
            try {
                // Pass the bitmap directly to the converter instead of preprocessing it
                String result = converter.convert(bitmap);
                runOnUiThread(() -> updateUIWithResult(result));
            } catch (Exception e) {
                runOnUiThread(() -> handleProcessingError(e));
            }
        });
        conversionThread.start();
    }

    private void updateUIWithResult(String result) {
        binding.resultText.setText(result);
        UIHelper.updateUIState(binding, true);
    }

    private void handleProcessingError(Exception e) {
        binding.resultText.setText("Lỗi: " + e.getMessage());
        UIHelper.updateUIState(binding, true);
    }

    private void handleConverterInitError(Exception e) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Lỗi khởi tạo: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ImageHandler.handleActivityResult(this, requestCode, resultCode, data, 
            binding, cameraImageUri, this::processImage);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionManager.handlePermissionResult(this, requestCode, grantResults);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (converter != null) {
            converter.cancelConversion();
        }
        if (conversionThread != null && conversionThread.isAlive()) {
            conversionThread.interrupt();
        }
    }

    @Override
    public void onBackPressed() {
        if (conversionThread != null && conversionThread.isAlive() && 
            binding.cancelButton.getVisibility() == View.VISIBLE) {
            converter.cancelConversion();
            Toast.makeText(this, "Đang hủy thao tác...", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}