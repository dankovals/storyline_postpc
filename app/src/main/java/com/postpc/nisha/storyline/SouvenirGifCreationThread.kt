package com.postpc.nisha.storyline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import com.postpc.nisha.storyline.utils.getCroppedBitmap
import java.io.File
import com.postpc.nisha.storyline.classifier.*
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


//var SPINNER_VISIBLE = 1
//var SPINNER_GONE = 2

var NUMBER_OF_IMAGES_IN_GIF = 10

class SouvenirGifCreationThread(classifier: Classifier, appContext: Context, storyName: String,
                                storyStartDate: String, storyEndDate: String, storyStartTime: String, storyEndTime: String):Thread() {

    private val selectedImagesPaths:ArrayList<ClassifiedImage> = ArrayList(0)
    private var classifier: Classifier
    private var context:Context
    private var appDirectory: String = ""
    private var gifOutputPath: String = ""
    private var gifFolderPath: String = ""
    private var tempThumbsFolderPath = ""
    private var storyStartDate: String = ""
    private var storyEndDate: String = ""
    private var storyEndTime: String = ""
    private var storyStartTime: String = ""
    private val myFormat = SimpleDateFormat("dd/MM/yyyy")
    private var storyImage:Bitmap? = null
    var storiesDb: DbForStoriesHelper = LoginActivity.getStoriesDb()
    var curStoryName: String = storyName
    private var isEmptyGif: String = ""

    init{
        this.classifier = classifier
        this.context = appContext

        this.appDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).absolutePath + "/Storyline/"
        this.gifFolderPath = appDirectory + curStoryName + "/"
        this.gifOutputPath = gifFolderPath + curStoryName + ".gif"
        tempThumbsFolderPath = appDirectory + "Temp/"
        val thumbsFolder = File(tempThumbsFolderPath)
        thumbsFolder.mkdirs()

        this.storyStartDate = storyStartDate
        this.storyEndDate = storyEndDate
        this.storyStartTime = storyStartTime
        this.storyEndTime = storyEndTime
    }

    override fun run() {

        println("IN SouvenirGifCreationThread run()!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
//        setWaitingSpinnerVisible()

        var directoryFileObserver = DirectoryFileObserver(appDirectory, curStoryName, context)
        directoryFileObserver.startWatching()

        classifyImages()
        createGif()
        storeGifInDb()

        val observerDirectory = File(appDirectory + "observer/")
        observerDirectory.mkdirs()
        observerDirectory.delete()

//        setWaitingSpinnerGone()
    }

    private fun classifyImages() {
        val relativeFolderPath = FinishStory.getImagesDir(storiesDb)
        val startDate: Calendar = Calendar.getInstance()
        startDate.setTime(myFormat.parse(storyStartDate))
        val endDate: Calendar = Calendar.getInstance()
        endDate.setTime(myFormat.parse(storyEndDate))
        AlbumActivity.setHourOfCalender(storyStartTime, startDate)
        AlbumActivity.setHourOfCalender(storyEndTime, endDate)
        val imagesPaths = AlbumActivity.setUpImagesFromCamera(relativeFolderPath, startDate, endDate, context)

        var counter = 0
        for (path in imagesPaths)
        {
            val image = File(path)
            println("HANDLE IMAGE NUMBER " + Integer.toString(counter) + ":")
            counter++
            val imageResult = classifyPhoto(image)
            val classifiedImage = ClassifiedImage(path, imageResult)
            addBestImagesPaths(classifiedImage)
        }
//        storyImage = setStoryImage(selectedImagesPaths.get(0).path)
    }

    private fun classifyPhoto(file: File): Result {
        val photoBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val croppedBitmap = getCroppedBitmap(photoBitmap)
        val result = classifier.recognizeImage(croppedBitmap)
        println("IMAGE RESULT IS: " + result)
        return result
    }

    private fun addBestImagesPaths(newClassifiedImage: ClassifiedImage){
        if (newClassifiedImage.classificationResult.result == "hot") {
            if (selectedImagesPaths.size < NUMBER_OF_IMAGES_IN_GIF) {
                selectedImagesPaths.add(newClassifiedImage)
            } else {
                Collections.sort(selectedImagesPaths, ClassifiedImageComparator())
                if (selectedImagesPaths.get(0).getClassificationResult().confidence
                        < newClassifiedImage.getClassificationResult().confidence) {
                    selectedImagesPaths.set(0, newClassifiedImage)
                }
            }
        }
    }

    fun createGif() {

        val thumbnailsPaths = ArrayList<String>()
        try {
            val writer = AnimatedGIFWriter(false)
            var gif = FileOutputStream(gifOutputPath)
            var gifImages = ArrayList<Bitmap>()
            var counter = 0

            if (selectedImagesPaths.size == 0) {
                isEmptyGif = "true"
            }
            else {
                isEmptyGif = "false"
                for (image in selectedImagesPaths) {
                    val bitmapOptions = BitmapFactory.Options()
                    val imageThumbnailPath = createThumbnail(image.path, counter)
                    thumbnailsPaths.add(imageThumbnailPath)
                    val imageThumbnail = BitmapFactory.decodeFile(imageThumbnailPath, bitmapOptions)
                    gifImages.add(imageThumbnail)
                    counter++
                }
            }

            // create the gif
            // using -1 for both logical screen width and height to use the first frame dimension
            writer.prepareForWrite(gif, -1, -1)
            for (image in gifImages) {
                writer.writeFrame(gif, image, 1000)
            }
            writer.finishWrite(gif)
            println("GIF CREATED!!!!!!!!!!!!!!!!!!!!!!!!!")
            println("GIF PATH: " + gifOutputPath)

            cleanThumbnails(thumbnailsPaths)

            println("THUMBS DELETED!!!!!!!!!!!!!!!!!!!!!!")
        } catch (e: Exception) {
            cleanThumbnails(thumbnailsPaths)
            e.printStackTrace()
        }
    }

    /**
     * create thumbnail for a given image.
     * @param inFilePath image's path
     * @param i image index (for the thumbnail name)
     * @return thumbnail path
     */
    fun createThumbnail(inFilePath: String, i: Int): String {
        var outThumbnail = ""
        try {
            val file = File(inFilePath) // the image file
            val bitmapOptions = BitmapFactory.Options()

            bitmapOptions.inJustDecodeBounds = true // obtain the size of the image, without
            // loading it in memory
            BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)

            // find the best scaling factor for the desired dimensions
            // (image resolution: https://microscope-microscope.org/microscope-info/image-resolution/)
            val desiredWidth = 1600//2080 // 400
            val desiredHeight = 1200//1542 // 300
            val widthScale = bitmapOptions.outWidth.toFloat() / desiredWidth
            val heightScale = bitmapOptions.outHeight.toFloat() / desiredHeight
            val scale = Math.min(widthScale, heightScale)

            var sampleSize = 1
            while (sampleSize < scale) {
                sampleSize *= 2
            }
            bitmapOptions.inSampleSize = sampleSize // this value must be a power of 2,
            // this is why we can't have an image scaled as we would like
            bitmapOptions.inJustDecodeBounds = false // now we want to load the image

            // load just the part of the image necessary for creating the thumbnail (not the whole image)
            val thumbnail = BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)

            // Save the thumbnail
            outThumbnail = gifFolderPath + "_" + Integer.toString(i + 1) + "THUMB.jpg"
            val thumbnailFile = File(outThumbnail)
            val fos = FileOutputStream(thumbnailFile)
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()

            // recycle the thumbnail
            thumbnail.recycle()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return outThumbnail
    }

    /**
     * delete all thumbnails
     * @param thumbnailsPaths arrayList of thumbnails Paths
     */
    fun cleanThumbnails(thumbnailsPaths: ArrayList<String>) {
        for (path in thumbnailsPaths) {
            deleteFile(File(path))
        }
    }

    fun deleteFile(file: File) {
        file.delete()
        if (file.exists()) {
            try {
                file.canonicalFile.delete()
            } catch (e: IOException) {
                Log.d("delete file exception", "exception caused by " + "file.getCanonicalFile().delete() in deleteFile function")
            }

            //            if(file.exists()){
            //                getApplicationContext().deleteFile(file.getName());
            //            }
        }
    }

//    fun setWaitingSpinnerVisible(){
//        var messageVISIBLE = waitingSpinnerHandler.obtainMessage(SPINNER_VISIBLE)
//        messageVISIBLE.sendToTarget()
//    }
//
//    fun setWaitingSpinnerGone(){
//        var messageVISIBLE = waitingSpinnerHandler.obtainMessage(SPINNER_GONE)
//        messageVISIBLE.sendToTarget()
//    }


    fun storeGifInDb(){
        storiesDb.insertGifDir(curStoryName, gifOutputPath, isEmptyGif)
    }


    fun setStoryImage(imagePath: String){ // TODO: set it to storyImage variable
        var thumb = createThumbnail(imagePath, 0)
        //TODO: do that createThumbnail will get as input the thumbnail output path, and then create
        // somewhere the story image (thumbnail), add this image to the db, and then delete the image
        // from its temp location
    }

}


