# AI-Background-Eraser
AI Background Eraser

## Features: 
* ### Erase the background of any photo Manually.
![20230401_090117](https://user-images.githubusercontent.com/99060332/229325938-afd26a02-0a7c-49b4-ba35-816a45dc0368.jpg)

* ### Auto color remover(after selecting a color form a photo this feature remove this color from every place from that picture)
![20230401_090254](https://user-images.githubusercontent.com/99060332/229325993-23e33d76-379a-4b27-a2df-8bd63003af16.jpg)

* ### Auto Selfie feature(using ML Kit's Selfie Segmentation API), after clicking this button this feature remove the hole background automatically. 
![20230401_090334](https://user-images.githubusercontent.com/99060332/229326283-c23ea6c5-0219-4260-a74d-ee4a3c452b2d.jpg)


>Step 1. Add the JitPack repository to your build file

```gradle

allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
 
>Step 2. Add the dependency

```gradle
dependencies {
	        implementation 'com.github.ShubhadeepKarmakar:AI-Background-Eraser:Tag'
	}
```
