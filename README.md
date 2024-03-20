PulseDroid - simple TCP protocol player.
-------------------------------------

PulseDroid is an audio player for Android that plays pcm16le mono/stereo
audio from a simple TCP connection, as provided by PulseAudio/PipeWire,
but also by a few other implementations.

PulseAudio/PipeWire has `module-simple-protocol-tcp` that provides such an endpoint, which
allows streaming audio to, among others, this app.

## Setup

1. Load `module-simple-protocol-tcp`.

   Warning: this PulseAudio module provides an endpoint that allows anyone in
   your network to listen to the connected source, and to play back audio.
   Make sure that your network is trusted or that you secure the connection
   appropriately, e.g. using a TLS tunnel or WireGuard.

   Run the following to add and configure the module; of course, the port and source can be
   modified as you want, and rate can be either 44100 or 48000, and channels can be either 1 (mono)
   or 2 (stereo),
   depending on what you'll set in the app:

        pactl load-module module-simple-protocol-tcp rate=44100 format=s16le channels=1 source=0 record=true port=12345 listen=0.0.0.0

   In case you want to unload, simply run:

        pactl unload-module module-simple-protocol-tcp

   or, if you have multiple TCP sinks and want to unload a specific one, unload the number returned
   in the the first command for it (the number is an example):

        pactl unload-module 536870918

2. Install the app and start it.

3. Enter the host and port of your PulseAudio endpoint and hit play.

The `source=` argument specifies the default source of connecting clients. It
can be changed for connected clients using a configuration program like
`pavucontrol`, or from the command line.

TODO: more advanced setup possible

## History

The original project is
[https://github.com/dront78/PulseDroid](https://github.com/dront78/PulseDroid),
but it was immediately abandoned after the first commit, leaving it in a very bare-bones state.

[https://github.com/Konubinix/PulseDroid](https://github.com/Konubinix/PulseDroid)
briefly picked up the project about 5 years later, improving the android
experience, for example by using a foreground service.

In 2018, ferreum, was looking for just such an app, found this project and
reworked everything, wanting a more polished experience. The app can now play
audio in the background after closing the main screen, has improved buffering,
error handling, state handling, and a modernized structure.

In 2022, [kescher](https://github.com/kescherCode/PulseDroid) added a few options for sample rate
and channel count adjustments,
as well as ways to automatically start the foreground service with the set host and port,
as well an option to restart the stream whenever there's an error.

In 2024, this app
got [published into the Play Store](https://play.google.com/store/apps/details?id=at.kescher.pulsedroid).

And now a message from the original creator at the beginning of this project:

> This software is a some kind of network music player I done for my personal purposes.
> I use it with PulseAudio over network to watch a video or listen music from my pc with in a
> headphones ;)
>
> It is very simple to setup a PulseAudio server to send audio output over network
>
>     pactl load-module module-simple-protocol-tcp rate=44100 format=s16le channels=1 source=alsa_output.pci-0000_00_1b.0.analog-stereo.monitor record=true port=server_port listen=ip_address
>                                                                                             ^^^^^ change this with oyur own device ^^^^^^^
>
> And now you just connect to ip:port via PulseDroid software and enjoy
>
> Hope it will be very useful somewhere for someone else.
>
> Regards, Ivan ;)

And useful it was. Thanks Ivan.
