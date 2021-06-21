PulseDroid - Network raw audio player
-------------------------------------

PulseDroid is an audio player for Android that plays 48000Hz pcm16le stereo
audio from a simple TCP connection.

PulseAudio has `module-simple-protocol-tcp` that provides such an endpoint, which
allows streaming audio to an Android device.

## Setup

1. Load `module-simple-protocol-tcp`.

    Warning: this PulseAudio module provides an endpoint that allows anyone in
    your network to listen to the connected source, and to play back audio.
    Make sure that your network is trusted or that you secure the connection
    appropriately.

    Run the following to add and configure the module; of course, the port and source can be modified as you want:

        pactl load-module module-simple-protocol-tcp rate=48000 format=s16le channels=2 source=0 record=true port=12345 listen=0.0.0.0

2. Install the app and start it.

3. Enter the host and port of your PulseAudio endpoint and hit play.

The `source=` argument specifies the default source of connecting clients. It
can be changed for connected clients using a configuration program like
`pavucontrol`, or from the command line.

TODO: more advanced setup possible

## History

The original project is
[https://github.com/dront78/PulseDroid](https://github.com/dront78/PulseDroid),
but it has been immediately abandoned after the first commit, leaving it in
very bare-bones state.

[https://github.com/Konubinix/PulseDroid](https://github.com/Konubinix/PulseDroid)
shortely picked up the project about 5 years later, improving the android
experience, for example by using a foreground service.

In 2018 I, ferreum, was looking for just such an app, found this project and
reworked everything, wanting a more polished experience. The app can now play
audio in the background after closing the main screen, has improved buffering,
error handling, state handling, and a modernized structure. More improvements
to follow as I see fit.

And now a message from the original creator at the beginning of this project:

> This software is a some kind of network music player I done for my personal purposes.
> I use it with PulseAudio over network to watch a video or listen music from my pc with in a headphones ;)
>
> It is very simple to setup a PulseAudio server to send audio output over network
>
>     pactl load-module module-simple-protocol-tcp rate=48000 format=s16le channels=2 source=alsa_output.pci-0000_00_1b.0.analog-stereo.monitor record=true port=server_port listen=ip_address
>                                                                                             ^^^^^ change this with oyur own device ^^^^^^^
>
> And now you just connect to ip:port via PulseDroid software and enjoy
>
> Hope it will be very useful somewhere for someone else.
>
> Regards, Ivan ;)

And useful it was. Thanks Ivan.
