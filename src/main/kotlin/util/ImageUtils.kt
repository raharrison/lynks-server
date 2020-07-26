package util

import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageUtils {
    fun scaleToDimensions(raw: ByteArray, width: Int, height: Int): ByteArray {
        val img = ImageIO.read(ByteArrayInputStream(raw))
        val scaledImage = img.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        val imageBuff = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        imageBuff.graphics.drawImage(scaledImage, 0, 0, Color.BLACK, null)
        imageBuff.graphics.dispose()
        val buffer = ByteArrayOutputStream()
        ImageIO.write(imageBuff, "jpg", buffer)
        return buffer.toByteArray()
    }

    fun cropImage(
        raw: ByteArray,
        width: Int,
        height: Int
    ): ByteArray {
        val image = ImageIO.read(ByteArrayInputStream(raw))
        val cropped = image.getSubimage(0, 0, width, height);
        val buffer = ByteArrayOutputStream()
        ImageIO.write(cropped, "jpg", buffer)
        return buffer.toByteArray()
    }
}
