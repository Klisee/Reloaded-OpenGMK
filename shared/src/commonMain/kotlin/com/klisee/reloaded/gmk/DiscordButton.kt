package com.klisee.reloaded.gmk

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Invitación del Discord del proyecto. */
const val DISCORD_INVITE_URL = "https://discord.gg/c8D3X78zhg"

private val DiscordBlurple = Color(0xFF5865F2)

/**
 * Logo de Discord dibujado con [PathBuilder] en vez de un string de path.
 *
 * Un `pathData` en texto obliga al parser de Compose a interpretar los flags de los arcos, y con
 * este logo (arcos con flags pegados) lo hacía mal: el ojo izquierdo se pintaba como una mancha.
 * Con coordenadas ya convertidas a curvas absolutas no hay nada que interpretar.
 *
 * El relleno es [PathFillType.EvenOdd] porque el logo son tres subpaths (cuerpo y dos ojos) y los
 * ojos tienen que quedar huecos, no pintados encima.
 */
private val DiscordLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "Discord",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.EvenOdd,
    ) {
        moveTo(20.317f, 4.370f)
        curveTo(20.123f, 4.281f, 19.927f, 4.195f, 19.730f, 4.112f)
        curveTo(19.533f, 4.029f, 19.335f, 3.949f, 19.135f, 3.873f)
        curveTo(18.936f, 3.797f, 18.735f, 3.723f, 18.533f, 3.654f)
        curveTo(18.331f, 3.584f, 18.128f, 3.517f, 17.924f, 3.454f)
        curveTo(17.720f, 3.390f, 17.514f, 3.330f, 17.308f, 3.274f)
        curveTo(17.102f, 3.217f, 16.895f, 3.164f, 16.688f, 3.114f)
        curveTo(16.480f, 3.064f, 16.271f, 3.017f, 16.062f, 2.974f)
        curveTo(15.852f, 2.931f, 15.643f, 2.891f, 15.432f, 2.855f)
        curveTo(15.428f, 2.854f, 15.424f, 2.853f, 15.420f, 2.853f)
        curveTo(15.416f, 2.853f, 15.413f, 2.853f, 15.409f, 2.854f)
        curveTo(15.405f, 2.854f, 15.401f, 2.855f, 15.398f, 2.856f)
        curveTo(15.394f, 2.857f, 15.390f, 2.859f, 15.387f, 2.860f)
        curveTo(15.383f, 2.862f, 15.380f, 2.864f, 15.377f, 2.866f)
        curveTo(15.373f, 2.868f, 15.370f, 2.871f, 15.368f, 2.873f)
        curveTo(15.365f, 2.876f, 15.362f, 2.879f, 15.360f, 2.882f)
        curveTo(15.357f, 2.885f, 15.355f, 2.888f, 15.353f, 2.892f)
        curveTo(15.142f, 3.267f, 14.909f, 3.756f, 14.745f, 4.141f)
        curveTo(12.900f, 3.865f, 11.065f, 3.865f, 9.258f, 4.141f)
        curveTo(9.095f, 3.748f, 8.852f, 3.267f, 8.641f, 2.892f)
        curveTo(8.639f, 2.888f, 8.636f, 2.885f, 8.634f, 2.882f)
        curveTo(8.632f, 2.879f, 8.629f, 2.877f, 8.626f, 2.874f)
        curveTo(8.623f, 2.871f, 8.620f, 2.869f, 8.617f, 2.867f)
        curveTo(8.614f, 2.865f, 8.611f, 2.863f, 8.607f, 2.861f)
        curveTo(8.604f, 2.859f, 8.600f, 2.858f, 8.596f, 2.857f)
        curveTo(8.593f, 2.856f, 8.589f, 2.855f, 8.585f, 2.854f)
        curveTo(8.581f, 2.854f, 8.577f, 2.854f, 8.574f, 2.854f)
        curveTo(8.570f, 2.854f, 8.566f, 2.854f, 8.562f, 2.855f)
        curveTo(8.351f, 2.891f, 8.142f, 2.931f, 7.932f, 2.974f)
        curveTo(7.723f, 3.017f, 7.514f, 3.064f, 7.306f, 3.114f)
        curveTo(7.099f, 3.163f, 6.892f, 3.217f, 6.686f, 3.273f)
        curveTo(6.479f, 3.330f, 6.274f, 3.390f, 6.070f, 3.453f)
        curveTo(5.866f, 3.517f, 5.663f, 3.583f, 5.461f, 3.653f)
        curveTo(5.259f, 3.723f, 5.058f, 3.796f, 4.859f, 3.873f)
        curveTo(4.659f, 3.949f, 4.461f, 4.029f, 4.264f, 4.112f)
        curveTo(4.067f, 4.194f, 3.871f, 4.280f, 3.677f, 4.370f)
        curveTo(3.675f, 4.370f, 3.674f, 4.371f, 3.672f, 4.372f)
        curveTo(3.670f, 4.373f, 3.669f, 4.374f, 3.667f, 4.375f)
        curveTo(3.666f, 4.376f, 3.664f, 4.377f, 3.663f, 4.378f)
        curveTo(3.662f, 4.379f, 3.660f, 4.380f, 3.659f, 4.381f)
        curveTo(3.657f, 4.382f, 3.656f, 4.383f, 3.655f, 4.385f)
        curveTo(3.654f, 4.386f, 3.652f, 4.387f, 3.651f, 4.389f)
        curveTo(3.650f, 4.390f, 3.649f, 4.391f, 3.648f, 4.393f)
        curveTo(3.647f, 4.394f, 3.646f, 4.396f, 3.645f, 4.397f)
        curveTo(0.533f, 9.046f, -0.319f, 13.580f, 0.099f, 18.058f)
        curveTo(0.100f, 18.061f, 0.100f, 18.063f, 0.101f, 18.066f)
        curveTo(0.101f, 18.069f, 0.102f, 18.071f, 0.103f, 18.074f)
        curveTo(0.103f, 18.076f, 0.104f, 18.079f, 0.105f, 18.082f)
        curveTo(0.107f, 18.084f, 0.108f, 18.087f, 0.109f, 18.089f)
        curveTo(0.110f, 18.091f, 0.112f, 18.094f, 0.114f, 18.096f)
        curveTo(0.115f, 18.098f, 0.117f, 18.100f, 0.118f, 18.102f)
        curveTo(0.120f, 18.105f, 0.122f, 18.107f, 0.124f, 18.108f)
        curveTo(0.126f, 18.110f, 0.128f, 18.112f, 0.130f, 18.114f)
        curveTo(2.183f, 19.622f, 4.172f, 20.537f, 6.123f, 21.143f)
        curveTo(6.127f, 21.144f, 6.131f, 21.145f, 6.135f, 21.146f)
        curveTo(6.139f, 21.146f, 6.143f, 21.147f, 6.147f, 21.146f)
        curveTo(6.151f, 21.146f, 6.154f, 21.146f, 6.158f, 21.145f)
        curveTo(6.162f, 21.145f, 6.166f, 21.144f, 6.170f, 21.143f)
        curveTo(6.173f, 21.142f, 6.177f, 21.140f, 6.181f, 21.138f)
        curveTo(6.184f, 21.136f, 6.188f, 21.134f, 6.191f, 21.132f)
        curveTo(6.194f, 21.130f, 6.197f, 21.127f, 6.200f, 21.125f)
        curveTo(6.202f, 21.122f, 6.205f, 21.119f, 6.207f, 21.116f)
        curveTo(6.669f, 20.485f, 7.081f, 19.820f, 7.434f, 19.122f)
        curveTo(7.436f, 19.117f, 7.438f, 19.112f, 7.439f, 19.107f)
        curveTo(7.441f, 19.101f, 7.441f, 19.096f, 7.442f, 19.091f)
        curveTo(7.442f, 19.085f, 7.442f, 19.080f, 7.441f, 19.075f)
        curveTo(7.440f, 19.070f, 7.438f, 19.064f, 7.436f, 19.059f)
        curveTo(7.434f, 19.054f, 7.432f, 19.050f, 7.429f, 19.045f)
        curveTo(7.426f, 19.041f, 7.423f, 19.037f, 7.419f, 19.033f)
        curveTo(7.415f, 19.029f, 7.411f, 19.026f, 7.406f, 19.023f)
        curveTo(7.402f, 19.020f, 7.397f, 19.018f, 7.392f, 19.016f)
        curveTo(6.739f, 18.768f, 6.118f, 18.466f, 5.520f, 18.123f)
        curveTo(5.514f, 18.120f, 5.509f, 18.116f, 5.505f, 18.112f)
        curveTo(5.500f, 18.108f, 5.496f, 18.103f, 5.493f, 18.097f)
        curveTo(5.490f, 18.092f, 5.487f, 18.086f, 5.485f, 18.080f)
        curveTo(5.483f, 18.074f, 5.482f, 18.068f, 5.482f, 18.062f)
        curveTo(5.481f, 18.055f, 5.482f, 18.049f, 5.483f, 18.043f)
        curveTo(5.484f, 18.037f, 5.486f, 18.031f, 5.489f, 18.025f)
        curveTo(5.491f, 18.019f, 5.495f, 18.014f, 5.499f, 18.009f)
        curveTo(5.503f, 18.004f, 5.507f, 18.000f, 5.512f, 17.996f)
        curveTo(5.638f, 17.901f, 5.764f, 17.803f, 5.884f, 17.704f)
        curveTo(5.887f, 17.702f, 5.889f, 17.700f, 5.892f, 17.698f)
        curveTo(5.895f, 17.697f, 5.898f, 17.695f, 5.901f, 17.694f)
        curveTo(5.905f, 17.692f, 5.908f, 17.691f, 5.911f, 17.690f)
        curveTo(5.914f, 17.689f, 5.918f, 17.689f, 5.921f, 17.688f)
        curveTo(5.925f, 17.688f, 5.928f, 17.687f, 5.931f, 17.687f)
        curveTo(5.935f, 17.687f, 5.938f, 17.688f, 5.942f, 17.688f)
        curveTo(5.945f, 17.689f, 5.949f, 17.689f, 5.952f, 17.690f)
        curveTo(5.955f, 17.691f, 5.958f, 17.692f, 5.962f, 17.694f)
        curveTo(9.889f, 19.487f, 14.142f, 19.487f, 18.023f, 17.694f)
        curveTo(18.026f, 17.692f, 18.029f, 17.691f, 18.033f, 17.690f)
        curveTo(18.036f, 17.689f, 18.039f, 17.688f, 18.043f, 17.688f)
        curveTo(18.046f, 17.687f, 18.050f, 17.687f, 18.053f, 17.687f)
        curveTo(18.057f, 17.687f, 18.060f, 17.687f, 18.064f, 17.687f)
        curveTo(18.067f, 17.688f, 18.070f, 17.688f, 18.074f, 17.689f)
        curveTo(18.077f, 17.690f, 18.080f, 17.691f, 18.084f, 17.693f)
        curveTo(18.087f, 17.694f, 18.090f, 17.696f, 18.093f, 17.697f)
        curveTo(18.096f, 17.699f, 18.099f, 17.701f, 18.101f, 17.703f)
        curveTo(18.222f, 17.802f, 18.347f, 17.901f, 18.474f, 17.996f)
        curveTo(18.479f, 18.000f, 18.484f, 18.004f, 18.488f, 18.009f)
        curveTo(18.492f, 18.014f, 18.495f, 18.019f, 18.498f, 18.025f)
        curveTo(18.500f, 18.030f, 18.502f, 18.036f, 18.503f, 18.043f)
        curveTo(18.505f, 18.049f, 18.505f, 18.055f, 18.505f, 18.061f)
        curveTo(18.505f, 18.068f, 18.503f, 18.074f, 18.502f, 18.080f)
        curveTo(18.500f, 18.086f, 18.497f, 18.092f, 18.494f, 18.097f)
        curveTo(18.491f, 18.102f, 18.487f, 18.107f, 18.482f, 18.112f)
        curveTo(18.478f, 18.116f, 18.473f, 18.120f, 18.468f, 18.123f)
        curveTo(18.393f, 18.167f, 18.318f, 18.210f, 18.242f, 18.252f)
        curveTo(18.166f, 18.294f, 18.090f, 18.335f, 18.014f, 18.376f)
        curveTo(17.937f, 18.416f, 17.860f, 18.456f, 17.783f, 18.495f)
        curveTo(17.706f, 18.534f, 17.628f, 18.572f, 17.550f, 18.609f)
        curveTo(17.472f, 18.646f, 17.393f, 18.682f, 17.314f, 18.718f)
        curveTo(17.235f, 18.753f, 17.156f, 18.788f, 17.077f, 18.822f)
        curveTo(16.997f, 18.856f, 16.917f, 18.889f, 16.837f, 18.921f)
        curveTo(16.756f, 18.953f, 16.676f, 18.984f, 16.595f, 19.015f)
        curveTo(16.590f, 19.017f, 16.585f, 19.019f, 16.580f, 19.022f)
        curveTo(16.576f, 19.025f, 16.572f, 19.028f, 16.568f, 19.032f)
        curveTo(16.564f, 19.036f, 16.561f, 19.040f, 16.558f, 19.045f)
        curveTo(16.555f, 19.049f, 16.552f, 19.054f, 16.550f, 19.059f)
        curveTo(16.549f, 19.064f, 16.547f, 19.069f, 16.546f, 19.075f)
        curveTo(16.545f, 19.080f, 16.545f, 19.085f, 16.546f, 19.091f)
        curveTo(16.546f, 19.096f, 16.547f, 19.101f, 16.548f, 19.107f)
        curveTo(16.549f, 19.112f, 16.551f, 19.117f, 16.554f, 19.122f)
        curveTo(16.914f, 19.820f, 17.326f, 20.484f, 17.779f, 21.115f)
        curveTo(17.781f, 21.118f, 17.784f, 21.121f, 17.787f, 21.124f)
        curveTo(17.789f, 21.127f, 17.792f, 21.129f, 17.795f, 21.132f)
        curveTo(17.799f, 21.134f, 17.802f, 21.136f, 17.805f, 21.138f)
        curveTo(17.809f, 21.140f, 17.813f, 21.141f, 17.816f, 21.143f)
        curveTo(17.820f, 21.144f, 17.824f, 21.145f, 17.828f, 21.145f)
        curveTo(17.832f, 21.146f, 17.836f, 21.147f, 17.840f, 21.147f)
        curveTo(17.844f, 21.147f, 17.848f, 21.146f, 17.852f, 21.146f)
        curveTo(17.855f, 21.145f, 17.859f, 21.145f, 17.863f, 21.143f)
        curveTo(19.824f, 20.537f, 21.813f, 19.621f, 23.865f, 18.114f)
        curveTo(23.868f, 18.112f, 23.870f, 18.111f, 23.872f, 18.109f)
        curveTo(23.874f, 18.107f, 23.876f, 18.105f, 23.878f, 18.103f)
        curveTo(23.879f, 18.101f, 23.881f, 18.099f, 23.883f, 18.097f)
        curveTo(23.884f, 18.094f, 23.886f, 18.092f, 23.887f, 18.090f)
        curveTo(23.888f, 18.087f, 23.890f, 18.085f, 23.891f, 18.082f)
        curveTo(23.892f, 18.080f, 23.893f, 18.077f, 23.893f, 18.075f)
        curveTo(23.894f, 18.072f, 23.895f, 18.070f, 23.895f, 18.067f)
        curveTo(23.896f, 18.064f, 23.896f, 18.061f, 23.897f, 18.059f)
        curveTo(24.397f, 12.882f, 23.058f, 8.385f, 20.348f, 4.398f)
        curveTo(20.347f, 4.397f, 20.346f, 4.395f, 20.346f, 4.394f)
        curveTo(20.345f, 4.392f, 20.343f, 4.391f, 20.342f, 4.389f)
        curveTo(20.341f, 4.388f, 20.340f, 4.386f, 20.339f, 4.385f)
        curveTo(20.338f, 4.384f, 20.337f, 4.382f, 20.335f, 4.381f)
        curveTo(20.334f, 4.380f, 20.332f, 4.379f, 20.331f, 4.378f)
        curveTo(20.330f, 4.377f, 20.328f, 4.376f, 20.327f, 4.375f)
        curveTo(20.325f, 4.374f, 20.323f, 4.373f, 20.322f, 4.372f)
        curveTo(20.320f, 4.371f, 20.319f, 4.370f, 20.317f, 4.370f)
        close()
        moveTo(8.020f, 15.331f)
        curveTo(6.838f, 15.331f, 5.863f, 14.245f, 5.863f, 12.912f)
        curveTo(5.863f, 11.579f, 6.819f, 10.493f, 8.020f, 10.493f)
        curveTo(9.231f, 10.493f, 10.196f, 11.588f, 10.177f, 12.912f)
        curveTo(10.177f, 14.245f, 9.221f, 15.331f, 8.020f, 15.331f)
        close()
        moveTo(15.995f, 15.331f)
        curveTo(14.812f, 15.331f, 13.838f, 14.245f, 13.838f, 12.912f)
        curveTo(13.838f, 11.579f, 14.793f, 10.493f, 15.995f, 10.493f)
        curveTo(17.206f, 10.493f, 18.171f, 11.588f, 18.152f, 12.912f)
        curveTo(18.152f, 14.245f, 17.206f, 15.331f, 15.995f, 15.331f)
        close()
    }.build()
}

@Composable
fun DiscordCommunityButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DiscordBlurple,
            contentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = DiscordLogo,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.White,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Únete al Discord",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
