import com.github.imeszaros.ambilight.Ambilight
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.github.imeszaros.ambicast.AmbiCastServer

object JointspaceDevice : ConfigSpec("jointspace") {

    val host by required<String>(description = "Hostname or IP address of the JointSPACE enabled device.")
    val port by optional(1925, description = "Port of the JointSPACE server.")
    val apiVersion by optional("1", description = "JointSPACE API version.")
}

object MultiCast : ConfigSpec("multicast") {

    val groupAddress by optional("237.36.35.34", description = "Multicast group address to forward Ambilight data to.")
    val port by optional(41414, description = "Multicast client port.")
}

object RefreshRate : ConfigSpec("refresh-rate") {

    val millis by optional(33L, description = "The Ambilight sampling rate in milliseconds.")
}

val config = Config { addSpec(JointspaceDevice); addSpec(MultiCast); addSpec(RefreshRate) }
        .withSourceFrom.yaml.file("config.yml")
        .withSourceFrom.env()
        .withSourceFrom.systemProperties()

fun main(args: Array<String>) {
    val ambilight = Ambilight(
            config[JointspaceDevice.host],
            config[JointspaceDevice.port],
            config[JointspaceDevice.apiVersion])

    val server = AmbiCastServer(ambilight,
            config[MultiCast.groupAddress],
            config[MultiCast.port],
            config[RefreshRate.millis])

    Runtime.getRuntime().addShutdownHook(Thread(server::shutdown))
}