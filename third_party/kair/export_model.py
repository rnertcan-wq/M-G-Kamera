import torch,onnx,onnxruntime as ort,numpy as np,time,json,hashlib
from pathlib import Path
base=Path('/workspace/camera-ai')
torch.set_num_threads(2)
class DnCNN(torch.nn.Module):
 def __init__(self):
  super().__init__();layers=[]
  for i in range(20):
   layers.append(torch.nn.Conv2d(3 if i==0 else 64,3 if i==19 else 64,3,padding=1))
   if i!=19:layers.append(torch.nn.ReLU())
  self.model=torch.nn.Sequential(*layers)
 def forward(self,x):return x-self.model(x)
model=DnCNN();model.load_state_dict(torch.load(base/'dncnn_color_blind.pth',map_location='cpu',weights_only=True));model.eval()
input=torch.rand(1,3,96,96)
torch.onnx.export(model,input,str(base/'dncnn-color-blind.onnx'),input_names=['image'],output_names=['denoised'],opset_version=17,dynamic_axes={'image':{2:'height',3:'width'},'denoised':{2:'height',3:'width'}})
onnx.checker.check_model(onnx.load(base/'dncnn-color-blind.onnx'))
options=ort.SessionOptions();options.intra_op_num_threads=2
session=ort.InferenceSession(str(base/'dncnn-color-blind.onnx'),options,providers=['CPUExecutionProvider'])
with torch.no_grad():expected=model(input).numpy()
actual=session.run(None,{'image':input.numpy()})[0]
error=float(np.max(np.abs(expected-actual)));assert error<1e-4
rng=np.random.default_rng(8);clean=np.full((1,3,96,96),0.45,np.float32)
noise=(clean+rng.normal(0,15/255,clean.shape)).clip(0,1).astype(np.float32)
start=time.perf_counter();denoised=session.run(None,{'image':noise})[0];duration=time.perf_counter()-start
before=float(np.mean((noise-clean)**2));after=float(np.mean((denoised-clean)**2));assert after<before
# Tile halo=20 covers twenty 3x3 convolution layers; check against whole inference.
image=rng.uniform(.2,.8,(1,3,96,128)).astype(np.float32)
whole=session.run(None,{'image':image})[0];tiled=np.empty_like(whole)
for y in range(0,96,32):
 for x in range(0,128,32):
  y0=max(0,y-20);x0=max(0,x-20);y1=min(96,y+32+20);x1=min(128,x+32+20)
  tile=session.run(None,{'image':image[:,:,y0:y1,x0:x1].copy()})[0]
  tiled[:,:,y:y+32,x:x+32]=tile[:,:,y-y0:y-y0+32,x-x0:x-x0+32]
tileError=float(np.max(np.abs(whole-tiled)));assert tileError<1e-4
report={'model':'KAIR DnCNN color blind','license':'MIT (KAIR)','onnxSha256':hashlib.sha256((base/'dncnn-color-blind.onnx').read_bytes()).hexdigest(),'referenceMaxAbsoluteError':error,'tileMaxAbsoluteError':tileError,'syntheticInputMSE':before,'syntheticOutputMSE':after,'96x96InferenceSecondsHost':duration,'phoneRuntimeValidated':False,'realCameraQualityValidated':False}
(base/'model-validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2),flush=True)
