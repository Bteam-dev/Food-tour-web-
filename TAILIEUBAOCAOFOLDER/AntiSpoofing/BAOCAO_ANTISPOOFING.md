# TÀI LIỆU BÁO CÁO: FACE ANTI-SPOOFING
> EfficientNet-B0 / MobileNetV3-Small 6-channel | Dataset: LCC_FASD + CelebA + Real Attack Videos
> Spring Boot + ONNX Runtime Java | Combined scoring: Model Gate + Diff Metrics

---

## 1. TỔNG QUAN HỆ THỐNG

### Mục tiêu
Phát hiện giả mạo khuôn mặt trong quá trình đăng nhập bằng Face ID:
- Chặn ảnh in, ảnh trên màn hình, video replay, lắc ảnh/điện thoại
- Tích hợp sâu vào quy trình xác thực Face Authentication của app
- Chạy hoàn toàn on-device (Spring Boot CPU) không phụ thuộc API ngoài

### Vị trí trong hệ thống
```
Android App (CameraX)
    │ base64 JPEG frame + framePrev
    ▼
FaceAuthController (/api/face/verify)
    │
    ▼
FaceAuthServiceImpl
    ├── FaceEmbeddingExtractorService  ← ArcFace ONNX: nhận diện khuôn mặt
    ├── AntiSpoofService               ← CHỐNG GIẢ MẠO (module này)
    └── HeadPoseService                ← WHENet: active challenge nếu cần
```

---

## 2. CÁC LOẠI TẤN CÔNG VÀ CƠ CHẾ PHÁT HIỆN

### 2.1 Ảnh tĩnh (Print Attack)

| Biến thể | Đặc điểm | Signal phát hiện |
|---|---|---|
| Print LQ — in giấy thường | Noise σ cao, màu nhạt, mờ | Model: noise pattern; Diff: uniform noise |
| Print HD — in ảnh HD | Gần giống real, thiếu micro-texture da | Model: JPEG artifact, texture flatness |
| Screen Static — màn hình tĩnh | Blue shift nhẹ, moire pattern | Model: moire + gamma; Diff: tiny noise |
| Screen HD — màn hình HD | Gần giống real nhất | Model: subtle moire + gamma distortion |

### 2.2 Video Replay Attack

| Biến thể | Signal phát hiện |
|---|---|
| Replay LQ | Moire rõ, desaturation, noise |
| Replay HD (khó nhất) | Diff: RIGID motion (toàn frame dịch cùng hướng) |

### 2.3 Lắc ảnh/điện thoại — HARDEST ATTACK

```
Attacker lắc ảnh in hoặc điện thoại → tạo diff cao giống mặt thật

Observable:
  mean_abs_diff = 0.05-0.12   (CAO — trông như chuyển động thật)
  cv            = 1.28-1.51   (thấp — rigid motion, đồng hướng)
  model_score   = 0.07-0.16   (thấp — MODEL VẪN THẤY TEXTURE FAKE)

Phát hiện DUY NHẤT bởi: MODEL GATE (texture analysis)
Diff metrics bị đánh lừa hoàn toàn → không dùng được
```

---

## 3. KIẾN TRÚC MODEL

### 3.1 Input: 6 Channels — Tại sao?

```
Input [1, 6, 224, 224]:
┌───────────────────────────────────────────┐
│ Channels 0-2: RGB frame (ImageNet norm)   │ → Texture features
│   - Phát hiện: moire, JPEG artifact,      │
│     thiếu micro-texture da, gamma         │
├───────────────────────────────────────────┤
│ Channels 3-5: Diff map (temporal signal)  │ → Motion features
│   = (frame2 - frame1) / 255 + 0.5        │
│   - 0.5 = không đổi                       │
│   - >0.5 = sáng lên, <0.5 = tối đi       │
└───────────────────────────────────────────┘
```

**Tại sao diff map quan trọng:**

| Loại | Diff pattern |
|---|---|
| Real face | Spatial structure: mắt/miệng/da chuyển động cùng nhau, magnitude 5-20px |
| Print/Photo | Pure camera noise — đồng đều, không có structure |
| Video replay | Smooth periodic motion — quá đều đặn, FPS-consistent |
| Lắc ảnh | Rigid motion — toàn bộ frame dịch cùng 1 hướng |

### 3.2 Model V1: MobileNetV3-Small

```python
class AntiSpoofModel6Ch(nn.Module):
    def __init__(self):
        backbone = mobilenet_v3_small(weights=IMAGENET1K_V1)
        
        # Mở rộng first conv 3ch → 6ch
        new_conv = nn.Conv2d(6, out_channels, kernel_size, stride, padding)
        
        # Kế thừa ImageNet pretrained weights cho RGB
        new_conv.weight[:, :3] = old_conv.weight.data.clone()
        
        # Diff channels init = 0.1× (không phải 0 — để gradient chạy)
        new_conv.weight[:, 3:] = old_conv.weight.data.clone() * 0.1
        
        # Custom classifier head
        # Dropout(0.45) → Linear(in_f, 128) → Hardswish
        # → LayerNorm(128) → Dropout(0.3) → Linear(128, 2)
        
    def forward(self, x):  # x: [B, 6, 224, 224]
        return self.model(x)  # output: [B, 2] logits
```

**Tại sao diff init = 0.1× thay vì 0?**
- Init = 0 → gradient = 0 → diff channels không học được gì
- Init = 0.1× → gradient chạy được từ epoch 1, nhưng nhỏ đủ để không làm nhiễu RGB features

### 3.3 Model V3: EfficientNet-B0 (mạnh hơn)

```
4.37M trainable params (vs ~2M của MobileNetV3-Small)
SE blocks: channel attention → focus vào diff map khi cần
Diff channels init = 0.15×
Custom head: Dropout(0.4) → 1280→256 SiLU → LayerNorm → Dropout(0.3) → 256→128 → Dropout(0.2) → 128→2
```

**V3 giải quyết được HD fakes mà V2 không nhận ra:**
- V2 thực chất là "blur/noise detector" — ảnh HD sạch → model thấy "sạch = real" → PASS sai
- V3 thêm Print HD + Screen HD pairs → buộc model học subtle texture, không chỉ dựa noise

**Tại sao EfficientNet > MobileNetV3:**
- 2x capacity → học được subtle texture patterns của HD attacks
- SE blocks → attention theo channel → tự học khi nên dùng RGB vs diff
- Pretrained ImageNet features mạnh hơn

---

## 4. QUY TRÌNH TRAIN — TỪNG CELL COLAB

### Cell 1 — Setup (~2 phút)

```python
# Cài đặt
!pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
!pip install opencv-python-headless albumentations onnx onnxruntime scikit-learn kaggle

# Config
DEVICE = torch.device("cuda")  # T4 GPU
USE_AMP = True                 # Mixed precision → nhanh hơn, ít VRAM

# Paths
RAW       = Path("/content/raw/lcc")
PAIRS_DIR = Path("/content/pairs")
SAVE_PATH = Path("/content/best_6ch.pth")
ONNX_FP32 = Path("/content/antispoofing_fp32.onnx")
```

**AMP (Automatic Mixed Precision):** Tính toán float16 thay vì float32 → nhanh 2x, VRAM giảm 50%

### Cell 2 — Kaggle Token

```python
KAGGLE_TOKEN = "KGAT_paste_token_vao_day"
# Lấy từ kaggle.com/settings → API → Create New Token
```

### Cell 3 — Tải Dataset

**V1:** 1 dataset
```python
# LCC_FASD (~5GB từ Kaggle)
# ~1,942 real images + ~16,885 spoof images
run("kaggle datasets download -d faber24/lcc-fasd -p /content/raw/lcc --unzip")
```

**V3:** 4 datasets (~9GB tổng)
```python
RAW_LCC      = kagglehub.dataset_download("faber24/lcc-fasd")            # 5GB — baseline
RAW_CELEBA   = kagglehub.dataset_download("jessicali9530/celeba-dataset") # 1.4GB — anti-overfit 200K faces
RAW_ANTISPOOF= kagglehub.dataset_download("tapakah68/anti-spoofing")      # 1.3GB — real attack videos
RAW_PRINT_HD = kagglehub.dataset_download("axondata/photo-print-attacks") # 1.1GB — real HD print
```

| Dataset | Vai trò | Số lượng |
|---|---|---|
| LCC_FASD | Baseline anti-spoofing | 1,942 real + 16,885 spoof |
| CelebA | Chống overfit — đa dạng người | 200K faces, 10K+ người |
| tapakah68/anti-spoofing | Temporal patterns THẬT | Replay, printout, cut-out videos |
| axondata/photo-print-attacks | HD print artifacts THẬT | HD print từ 3K+ người |

### Cell 4 — Probe Dataset

```python
# In ra cấu trúc folder, đếm real/spoof
# Kiểm tra ratio để biết cần class weights không
```

### Cell 5 — Tạo Frame Pairs *(Core Cell — ~22-60 phút)*

#### Config (V1)

```python
VAL_RATIO      = 0.15    # 15% data cho validation
PAIRS_PER_REAL = 5       # mỗi ảnh real → 5 pairs → 1942×5 ≈ 9710 real pairs
FAKE_TYPES     = ["print"]
VIDEO_PER_REAL = 3       # 1500 real × 3 = 4500 video fake pairs
```

#### Config (V3) — 3 mức motion real + 5 loại fake

```python
# Real pairs — 3 mức chuyển động
# Still (30%): ±0.1°, ±0.5px  → Tránh model học "diff nhỏ = fake"
# Micro (40%): ±2px, ±0.6°    → Chớp mắt, thở, run nhẹ
# Normal(30%): ±4px, ±1.2°    → Quay đầu tự nhiên

# Fake pairs — 5 loại
# Print LQ   (25%): noise σ=4-9, desaturate 0.75-0.88
# Print HD   (30%): noise σ=1-3, color 0.93-0.98 (gần real)
# Screen HD  (25%): noise σ=1-3, slight blue shift
# Screen Sta (20%): noise σ=3-6, blue shift rõ
# Shaken: CelebA subset, rigid motion
```

#### Augmentation functions — chi tiết

**aug_real_micro_pair() — Tạo real pair thực tế**
```python
def aug_real_micro_pair(img, seed):
    # Frame 2 = Frame 1 + DELTA NHỎ (không phải 2 seed độc lập)
    angle1  = random.uniform(-2.0, 2.0)
    tx1, ty1 = random.randint(2, 6), random.randint(2, 6)

    # BUG CŨ: 2 independent seeds → angle cực đối → diff KHỔNG LỒ (>50px)
    # FIX: frame 2 = frame 1 + delta nhỏ
    angle2  = angle1 + random.uniform(-0.6, 0.6)   # ±0.6° delta
    tx2     = tx1 + random.randint(-2, 2)            # ±2px delta
    bright2 = bright1 * random.uniform(0.97, 1.03)  # ±3% delta
    
    # → diff map có spatial structure thực tế (5-20 pixel value)
    # → KHÔNG phải 2 ảnh biến đổi độc lập
```

**aug_fake_print() — Mô phỏng ảnh in giấy**
```python
def aug_fake_print(img, seed):
    img = ImageEnhance.Color(img).enhance(random.uniform(0.75, 0.88))  # desaturate
    img = ImageEnhance.Sharpness(img).enhance(random.uniform(0.6, 0.85))  # soft
    
    # Noise INDEPENDENT per call (KHÔNG seed) → diff = pure white noise
    sigma = random.uniform(4.0, 9.0)
    noise = np.random.normal(0, sigma, arr.shape)  # fully random
```

**aug_fake_video() — Mô phỏng video replay**
```python
def aug_fake_video(img, seed, frame_idx):
    # Smooth PERIODIC motion (dùng sine wave)
    phase = (base_seed % 50) / 50.0 * 2 * math.pi
    amplitude = random.uniform(1.5, 3.5)  # chỉ 1.5-3.5px
    offset_x = amplitude * math.sin(phase + frame_idx * 0.4)
    # → periodic motion, khác irregular real face motion
```

**Diff map kỳ vọng:**
```
Real:         diff MODERATE (5-20 pixel), SPATIAL STRUCTURE rõ (mắt/miệng cùng chuyển động)
Fake print:   diff NHỎ (3-9), UNIFORM (white noise, không có structure)
Fake video:   diff NHỎ-VỪA (2-6), SMOOTH và ĐỀU ĐẶN
```

**Tổng số pairs (V1):**
```
~9710 real + ~16885 print + ~4500 video = ~31095 total
Fake/Real ratio ≈ 2.2x → class weights bù
```

### Cell 6 — DataLoader

```python
BATCH_SIZE  = 64  # AMP mode (T4)
NUM_WORKERS = 2

# ImageNet normalization (PHẢI KHỚP với inference Java)
MEAN = [0.485, 0.456, 0.406]  # ImageNet R, G, B means
STD  = [0.229, 0.224, 0.225]  # ImageNet R, G, B stds

def normalize_rgb(img_np):
    x = img_np.astype(float32) / 255.0
    return ((x - MEAN) / STD).transpose(2, 0, 1)  # CHW

def compute_diff(f1, f2):
    d = (f2 - f1) / 255.0          # [-1, 1]
    return clip(d + 0.5, 0, 1)     # [0, 1], 0.5 = không đổi

# Build 6-channel tensor [6, 224, 224]
x = concat([normalize_rgb(f2), compute_diff(f1, f2)], axis=0)

# Online augmentation — CÙNG transform cho f1 VÀ f2
# Tại sao? → Nếu aug khác nhau tạo fake temporal diff → model học sai
aug_online_shared = A.Compose([
    A.RandomBrightnessContrast(0.06, p=0.3),
    A.HueSaturationValue(5, 10, 5, p=0.3),
    A.GaussianBlur(3, p=0.15),
    A.ImageCompression(80-100, p=0.25),
])

class PairDataset(Dataset):
    # Label: 0 = fake, 1 = real
```

### Cell 7 — Model & Training

```python
# Loss Function: Focal Loss
class FocalLoss(nn.Module):
    def __init__(self, alpha=class_weights, gamma=2.0):  # V1: gamma=2, V3: gamma=3
        # focal_loss = (1 - pt)^gamma × cross_entropy
        # gamma=2: easy examples weight giảm mạnh
        # Tập trung vào hard examples (video replay HD, ảnh HD)

# Class weights — bù cho imbalanced dataset
n_total = n_fake + n_real
w = n_total / (2 * [n_fake, n_real])
# Ví dụ: fake 21385, real 9710 → w_fake=0.73, w_real=1.6

# Optimizer + Scheduler
optimizer = AdamW(lr=8e-4, weight_decay=1e-3)  # V1
optimizer = AdamW(lr=5e-4, weight_decay=2e-3)  # V3

scheduler = OneCycleLR(
    max_lr          = 8e-4,
    pct_start       = 0.12,       # 12% đầu = warmup phase
    anneal_strategy = "cos",       # cosine annealing sau warmup
    div_factor      = 15.0,        # LR bắt đầu = max_lr/15 ≈ 5.3e-5
    final_div_factor= 1e4          # LR cuối = max_lr/1e4 = 8e-8
)

EPOCHS   = 40   # V1 (early stopping patience=7)
EPOCHS   = 23   # V3

# Gradient clipping — tránh gradient explode
nn.utils.clip_grad_norm_(model.parameters(), 1.0)

# AMP — float16 inference, float32 update
scaler = torch.amp.GradScaler("cuda")
with torch.amp.autocast("cuda"):
    out = model(imgs)
    loss = criterion(out, labels)
scaler.scale(loss).backward()
scaler.step(optimizer)
```

**V3 thêm:**
```python
# Mixup augmentation (alpha=0.2, prob=50%)
# Blend 2 samples ngẫu nhiên → model robust hơn
# Gradient accumulation steps=2 → effective batch = 128

# Mixup:
lam = np.random.beta(0.2, 0.2)
mixed_x = lam * x_a + (1 - lam) * x_b
loss = lam * criterion(out, y_a) + (1 - lam) * criterion(out, y_b)
```

**Training loop — Monitor ACER:**
```python
# ACER = (APCER + BPCER) / 2 — metric chính ISO/IEC 30107-3
# APCER (=FAR) = fake lọt qua → nguy hiểm (security)
# BPCER (=FRR) = real bị chặn → khó chịu UX

# Lưu model khi ACER tốt nhất (KHÔNG dùng accuracy)
if acer < best_acer:
    torch.save(model.state_dict(), SAVE_PATH)

# Early stopping
if no_improve >= PATIENCE:
    print("Early stop")
```

**Tại sao không monitor accuracy?**
- Dataset imbalanced (fake ~2.2x real)
- Model predict toàn fake → accuracy ~68% nhưng vô dụng
- ACER đo cân bằng 2 loại lỗi có ý nghĩa thực tế

### Cell 8 — Evaluation + Export ONNX

```python
# Confusion Matrix
TP = real đúng là real
TN = fake đúng là fake
FP = fake bị predict là real (NGUY HIỂM)
FN = real bị predict là fake (khó chịu UX)

FAR (%) = FP / (TN + FP) * 100   ← phải < 3%
FRR (%) = FN / (TP + FN) * 100   ← phải < 10%

# ROC curve → tìm threshold tối ưu
best_security_idx = argmin(|fpr - 0.02|)  # target FAR ≤ 2%
thresh_security   = thresholds[best_security_idx]

# Score distribution
# Real — mean: 0.919, std: 0.024 (training)  /  0.18-0.60 (production thực tế)
# Fake — mean: 0.082, std: 0.023 (training)  /  0.07-0.16 (production thực tế)
# Separation: 0.837 (training)  /  0.02 gap (production thực tế — rất mỏng!)

# Export ONNX FP32 (KHÔNG INT8 — Java ONNX Runtime không support ConvInteger)
torch.onnx.export(
    model, dummy_input, "antispoofing_fp32.onnx",
    opset_version=13,
    input_names=["input"], output_names=["output"],
    dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
)
# Benchmark: ~100-200ms/frame trên CPU
```

---

## 5. KẾT QUẢ TRAINING

| Metric | V1 (MobileNetV3) | V3 (EfficientNet) |
|---|---|---|
| Val Accuracy | 88-94% | **99.94%** |
| ROC AUC | 0.92-0.97 | **1.0000** |
| ACER | < 0.03 | **0.0006** |
| Score separation | > 0.30 | **0.837** |
| FAR @threshold 0.5 | < 3% | ~0% |
| FRR @threshold 0.5 | < 10% | ~0% |

---

## 6. BACKEND — AntiSpoofServiceImpl.java

### 6.1 Hằng số và ý nghĩa

```java
// Input preprocessing
INPUT_SIZE            = 224         // Resize về 224×224
STATIC_DIFF_THRESHOLD = 0.005f      // Diff < 0.005 → cùng 1 ảnh → FAKE

// ImageNet normalization (KHỚP với Cell 6 training)
MEAN = {0.485f, 0.456f, 0.406f}
STD  = {0.229f, 0.224f, 0.225f}

// Combined scoring weights
MODEL_WEIGHT = 0.75   // Model chiếm 75% — primary signal (texture-based)
DIFF_WEIGHT  = 0.125  // Diff hỗ trợ phát hiện fake tĩnh
CV_WEIGHT    = 0.125  // CV hỗ trợ phát hiện noise-like diff

// Sigmoid parameters
DIFF_CENTER  = 0.035  // mean_abs_diff < 0.035 → nghi ngờ fake
DIFF_SCALE   = 80.0   // độ dốc sigmoid
CV_CENTER    = 1.5    // real: cv ~1.5-2.0, fake rigid: ~1.3-1.5
CV_SCALE     = 3.0

// QUAN TRỌNG NHẤT
MODEL_GATE   = 0.17   // realProb < 0.17 → FAKE ngay, không tính combined
                       // Gap production: fake max=0.16, real min=0.18
```

### 6.2 Flow xử lý

```java
public LivenessResult predict(String base64Frame, String base64FramePrev) {
    // 1. Decode base64 → OpenCV Mat
    Mat frame = decodeMat(decodeBase64(base64Frame));
    Mat framePrev = ...;

    // 2. Layer 1: Identical-bytes guard
    float[] metrics = computeDiffMetrics(frame, framePrev);  // resize 64×64
    float meanDiff = metrics[0], stdDiff = metrics[1];
    float cv = stdDiff / meanDiff;
    if (meanDiff < 0.005f) return new LivenessResult(false, 0.0, true);

    // 3. Layer 2: Build 6-channel input
    float[] input = build6ChannelInput(frame, framePrev);
    // channels 0-2: (px / 255 - MEAN) / STD  ← ImageNet normalized
    // channels 3-5: clip((px - ppx) + 0.5, 0, 1)  ← diff map

    // 4. Layer 2: ONNX inference [1, 6, 224, 224] → [1, 2]
    double[] probs  = softmax(logits);
    double realProb = probs[1];

    // 5. Layer 2.5: Model gate — TRƯỚC combined scoring
    if (realProb < MODEL_GATE) return new LivenessResult(false, realProb, hasTemp);

    // 6. Layer 3+4: Combined scoring
    double diffSignal = sigmoid((meanDiff - 0.035) * 80);
    double cvSignal   = sigmoid((1.5 - cv) * 3);
    double finalScore = 0.75 * realProb + 0.125 * diffSignal + 0.125 * cvSignal;

    // 7. Decision
    boolean live = finalScore >= livenessThreshold;  // 0.30 từ application.properties
    return new LivenessResult(live, finalScore, hasTemp);
}
```

### 6.3 Build 6-channel input (Java → khớp Python training)

```java
private float[] build6ChannelInput(Mat frame, Mat framePrev) {
    Mat f  = resizeAndRgb(frame);     // 224×224, BGR→RGB, float32
    Mat fp = resizeAndRgb(framePrev);

    for (int row = 0; row < 224; row++) {
        for (int col = 0; col < 224; col++) {
            for (int ch = 0; ch < 3; ch++) {
                int offset = ch * 224 * 224 + row * 224 + col;
                float px  = fi.get(row, col, ch)  / 255.0f;
                float ppx = fpi.get(row, col, ch) / 255.0f;
                
                // Ch 0-2: RGB normalized (NCHW format)
                data[offset]             = (px - MEAN[ch]) / STD[ch];
                
                // Ch 3-5: diff map = clip((current - prev) + 0.5, 0, 1)
                data[3*224*224 + offset] = Math.max(0f, Math.min(1f, (px - ppx) + 0.5f));
            }
        }
    }
}
// Nếu không có framePrev: ch 3-5 = 0.5 (neutral, không có thông tin)
```

### 6.4 Combined Scoring — Phân tích

**Sigmoid mapping thực tế:**

| mean_abs_diff | diffSignal | Ý nghĩa |
|---|---|---|
| 0.01 | 0.08 | Gần như tĩnh → fake |
| 0.025 | 0.31 | Ít chuyển động → nghi |
| 0.035 | 0.50 | Trung tính |
| 0.05 | 0.77 | Chuyển động tự nhiên → real |
| 0.08 | 0.97 | Chuyển động nhiều → real |

| cv | cvSignal | Ý nghĩa |
|---|---|---|
| 2.8 | 0.02 | cv rất cao → nhiễu loạn |
| 1.5 | 0.50 | Trung tính |
| 1.3 | 0.65 | Chuyển động đồng hướng (lắc rigid) |

**Tại sao model chiếm 75%?**
- Khi lắc ảnh: diffSignal ~0.99, cvSignal ~0.65
- Nếu model weight < 75%: combined = 0.5×0.16 + 0.25×0.99 + ... = 0.33 → PASS sai
- 75% đảm bảo texture (model) luôn là tín hiệu quyết định

**Vẫn cần Model Gate vì:**
- Fake lắc mạnh: model=0.16, diffSignal=0.99 → combined = 0.75×0.16 + 0.125×0.99×2 = 0.3675 → PASS sai!
- Gate = 0.17 → chặn ngay trước khi tính combined

### 6.5 Score phân bố production thực tế

| Trường hợp | Model | Gate | Combined | Kết quả |
|---|---|---|---|---|
| Mặt thật (normal) | 0.40-0.60 | Pass | 0.45-0.67 | PASS |
| Mặt thật (borderline) | 0.18 | Pass (0.18 > 0.17) | 0.27 | PASS* |
| Fake lắc điện thoại | 0.07-0.16 | **BLOCK** | — | FAIL |
| Fake HD video | 0.07-0.12 | **BLOCK** | — | FAIL |
| Fake ảnh tĩnh HD | 0.06-0.13 | BLOCK | ~0.12 | FAIL |

*Borderline case (combined=0.27 < 0.30): cần monitor ánh sáng xấu

### 6.6 application.properties

```properties
face.model.antispoofing.path=src/main/resources/models/FaceAuth/antispoofing.onnx
face.liveness.threshold=0.30
```

---

## 7. DOMAIN GAP — VẤN ĐỀ THỰC TẾ

| | Training (synthetic) | Production (camera thật) |
|---|---|---|
| Real face score | 0.919 ± 0.024 | **0.18–0.60** |
| Fake score | 0.082 ± 0.023 | **0.07–0.16** |
| Gap | 0.837 | **0.02** |

**Nguyên nhân domain gap:**
- Training real pairs: geometric augmentation (xoay/dịch ảnh tĩnh)
- Production: JPEG compression từ Android, sensor noise thật, micro-expressions sinh học thật
- → temporal patterns hoàn toàn khác nhau

**Hệ quả:**
- Gate 0.17 vs real min 0.18: margin chỉ 0.01 → ánh sáng xấu có thể khiến real face fail
- Cần thu thập real camera data để retrain và mở rộng gap

---

## 8. PIPELINE HOÀN CHỈNH

### 8.1 Enrollment Flow

```
Android: camera → chụp 15 frames trong 2 giây
    │ frames: [base64_frame1, ..., base64_frame15]
    ▼
POST /api/face/enroll  (cần JWT)
    │
    ▼
FaceAuthServiceImpl.enroll():
  1. Validate: cần ít nhất 5 frames
  2. Với mỗi frame:
     → Haar Cascade: detect face, crop, align 5-point
     → ArcFace ONNX: extract 512-dim embedding
  3. averageEmbeddings(validEmbeddings) → L2 normalize
  4. Lưu embedding vào MySQL (face_embeddings table)
     CHỈ LƯU VECTOR — không lưu ảnh (privacy by design)
```

### 8.2 Verification Flow

```
Android: chụp frame hiện tại + framePrev (200ms trước)
    │
    ▼
POST /api/face/verify  (public endpoint)
    │
    ▼
FaceAuthServiceImpl.verify():
  1. Tìm user → lấy stored embedding
  
  2. ArcFace extract embedding từ frame mới
     Cosine similarity vs stored embedding
     similarity < 0.65 → FAIL ngay (không phải người này)
  
  3. AntiSpoofService.predict(frame, framePrev):
     → 5 lớp phòng thủ (xem diagram dưới)
  
  4. Decision logic:
     ├── FAKE + temporal available → FAIL ngay
     ├── similarity >= 0.82 AND isLive → PASS → issue JWT
     ├── similarity >= 0.65 (uncertain) → CHALLENGE_REQUIRED
     └── else → FAIL
```

### 8.3 Active Challenge Flow

```
Server → { challengeAction: "TURN_LEFT", challengeToken: "uuid...", TTL: 90s Redis }
    │
    ▼ User quay đầu theo hướng chỉ định
    │
    ▼
POST /api/face/challenge
    │
    ▼
FaceAuthServiceImpl.solveChallenge():
  1. Validate challenge token Redis (one-time use → xóa ngay)
  2. HeadPoseService.estimatePose(frame):
     → WHENet ONNX → yaw/pitch/roll angles
  3. verifyChallenge(pose, required_action):
     TURN_LEFT:  yaw < -15°
     TURN_RIGHT: yaw > 15°
     LOOK_UP:    pitch > 10°
     LOOK_DOWN:  pitch < -10°
  4. Re-verify face similarity (tránh attacker dùng mặt mình)
  5. PASS → issue JWT
```

---

## 9. PREPROCESSING CONSISTENCY — TRAIN vs INFERENCE

```python
# Train (Cell 6 Python)
rgb = (pixel / 255.0 - MEAN) / STD
diff = clip((frame2 - frame1) / 255.0 + 0.5, 0, 1)
```

```java
// Inference (AntiSpoofServiceImpl.java:165-166)
data[offset]         = (px - MEAN[ch]) / STD[ch];
data[c*h*w + offset] = Math.max(0f, Math.min(1f, (px - ppx) + 0.5f));
```

**Lưu ý:** Java dùng NCHW format `[6, 224, 224]` — channel first, khớp với PyTorch default.

---

## 10. FILES LIÊN QUAN

| File | Mô tả |
|---|---|
| `AntiSpoofServiceImpl.java` | Core: combined scoring + ONNX inference |
| `FaceAuthServiceImpl.java` | Decision logic (pass/fail/challenge) |
| `HeadPoseService.java` | Active challenge (head pose via WHENet) |
| `FaceEmbeddingExtractorServiceImpl.java` | ArcFace 512-dim embedding |
| `FaceAuthController.java` | REST endpoints: /enroll, /verify, /challenge |
| `application.properties` | face.liveness.threshold=0.30, model path |
| `antispoofing.onnx` | Model V3 (EfficientNet-B0, ~16.7MB FP32) |
| `arcface.onnx` | ArcFace R100 (~250MB) |
| `headpose.onnx` | WHENet (~30MB) |
| `haarcascade_frontalface_default.xml` | Face detection (~900KB) |
| `TaiLieu/AI/FaceAuth/face_antispoofing_colab.md` | Colab V1 notebook |
| `TaiLieu/AI/FaceAuth/face_antispoofing_colab_v3.md` | Colab V3 notebook |
| `TaiLieu/AI/FaceAuth/face_antispoofing_defense.md` | Defense analysis |

---

## 11. CÁC CÂU HỎI GIÁO VIÊN CÓ THỂ HỎI

### Về kiến trúc model

**Q: Tại sao dùng 6 channel thay vì 3 channel thông thường?**
> 3 channel chỉ học texture (noise/blur của fake). 6 channel thêm diff map là temporal signal —
> phân biệt chuyển động sinh học của mặt thật (spatial structure) vs chuyển động giả (noise đồng đều
> hoặc rigid motion). Đây là điểm then chốt để phát hiện video replay mà texture không phân biệt được.

**Q: Tại sao init diff channels = 0.1× weight thay vì zero hoặc random?**
> Init = 0: gradient = 0 → diff channels không học được gì suốt quá trình train.
> Init = random: quá nhiễu, làm lệch RGB features đã pretrained.
> Init = 0.1×: đủ nhỏ không làm nhiễu, đủ lớn để gradient backward hoạt động từ epoch 1.

**Q: Tại sao chọn MobileNetV3/EfficientNet mà không dùng ResNet hay VGG?**
> Tiêu chí: inference < 100ms trên CPU (production real-time).
> MobileNetV3: ~2MB, ~50ms CPU → nhẹ cho demo.
> EfficientNet-B0: ~16MB, ~100ms CPU → tốt hơn, detect được HD fakes.
> ResNet50: ~90MB, ~300ms → quá nặng. VGG: không có depthwise conv, chậm hơn.

**Q: Tại sao dùng LayerNorm thay vì BatchNorm trong classifier head?**
> BatchNorm normalize theo batch dimension → không ổn định khi batch_size=1 (inference).
> LayerNorm normalize theo feature dimension → hoạt động tốt với mọi batch size.

**Q: Tại sao dùng Focal Loss thay vì CrossEntropy thông thường?**
> Dataset imbalanced (fake ~2.2x real). Focal Loss với gamma=2: easy examples (print LQ dễ phân biệt)
> có weight giảm → model focus vào hard examples (video replay HD, ảnh HD tinh tế). CrossEntropy
> thông thường sẽ dominated bởi easy examples → model lazy, không học được hard cases.

**Q: Tại sao ACER thay vì accuracy?**
> Accuracy vô nghĩa với imbalanced dataset. Predict toàn fake → accuracy 68% nhưng không dùng được.
> ACER = (APCER + BPCER) / 2 cân bằng 2 loại lỗi có ý nghĩa:
> APCER (fake lọt qua) = nguy hiểm security; BPCER (real bị chặn) = hại UX.
> Chuẩn ISO/IEC 30107-3 quốc tế cho liveness detection.

### Về training

**Q: Tại sao không train trực tiếp trên real camera data?**
> Không có dataset real camera thật đủ lớn và đa dạng. Synthetic augmentation cho phép
> control chính xác loại attack nào được học. Trade-off: domain gap (training 0.92 vs production 0.18-0.60).
> Giải pháp: thu thập real data sau deployment để fine-tune.

**Q: Tại sao cần "Still real pairs" (30% real không chuyển động)?**
> Nếu tất cả real pairs đều có chuyển động → model học "diff nhỏ = fake, diff lớn = real".
> User thực tế đứng yên ổn định trước camera → bị block.
> Still pairs buộc model học texture da thật thay vì chỉ dựa vào diff magnitude.

**Q: Bug cũ trong augmentation là gì và đã fix thế nào?**
> Bug: dùng 2 independent seeds → frame1 xoay +20°, frame2 xoay -20° → diff = 160px (không thực tế).
> Model học "diff lớn = real" → production fail (mọi thứ đều có diff nhỏ).
> Fix: frame2 = frame1 + delta nhỏ (±0.6°, ±2px) → diff 5-20px như production thực tế.

**Q: OneCycleLR scheduler hoạt động thế nào?**
> Phase 1 (12% đầu): LR tăng từ max_lr/15 → max_lr (warmup).
> Phase 2 (88% còn): LR giảm cosine từ max_lr → max_lr/10000 (annealing).
> Ưu điểm: hội tụ nhanh với ít epochs, không bị kẹt local minimum.

### Về backend defense

**Q: Tại sao cần Model Gate (0.17) thay vì chỉ dùng combined score?**
> Trường hợp lắc ảnh/điện thoại: diff_signal ≈ 0.99 (diff cao do lắc mạnh).
> Không có gate: combined = 0.75×0.16 + 0.125×0.99 + 0.125×0.51 = 0.31 → PASS sai!
> Gate chặn hard: realProb < 0.17 → FAKE ngay, không tính combined.
> Model là tín hiệu duy nhất không thể bị game bằng chuyển động vật lý.

**Q: Threshold 0.17 và 0.30 calibrate thế nào?**
> Đo trên production data thực tế: fake max model score = 0.16, real min = 0.18.
> Gate = 0.17 (giữa gap).
> Combined threshold 0.30 = safety net: real faces có model thấp (0.18) + diff tốt = combined ≈ 0.27-0.35.

**Q: CV (coefficient of variation) là gì và tại sao dùng?**
> CV = std/mean của diff map pixels.
> Real face: non-rigid motion → diff không đều → CV cao (~1.5-2.0).
> Fake lắc cứng: rigid motion → tất cả pixel dịch cùng hướng → CV thấp (~1.3-1.5).
> Nhưng CV chỉ chiếm 12.5% weight vì có thể bị game bằng cách lắc với tốc độ không đều.

**Q: Domain gap có nguy hiểm không?**
> Có. Gap gate chỉ 0.01 (fake max 0.16, real min 0.18) → điều kiện ánh sáng xấu hoặc camera kém
> có thể khiến real face bị block (false rejection). Cần monitor và thu thập real camera data.

**Q: Tại sao không lưu ảnh trong database?**
> Privacy by design. Embedding là vector 512 chiều — không thể reverse engineer ra ảnh gốc.
> Nếu DB bị breach, attacker không có ảnh để tấn công.

### Về limitations

**Q: Model có thể bị bypass bằng cách nào?**
> 1. Deepfake video (FaceSwap, DeepFaceLab) — texture da thật → model score cao.
> 2. 3D silicon mask chất lượng cao — micro-texture gần giống skin.
> Không có training data cho 2 trường hợp này → đây là known limitation.

**Q: Tại sao không dùng FaceID/Face++/AWS Rekognition?**
> Đây là đồ án nghiên cứu — mục tiêu implement từ đầu.
> Chi phí API, privacy (gửi ảnh ra ngoài), latency mạng, vendor lock-in.
> Toàn bộ inference chạy trong Spring Boot CPU, không phụ thuộc internet.
