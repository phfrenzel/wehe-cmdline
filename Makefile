image_name = wehe-cmdline-build
CE = podman

wehe-client.jar: src
	$(CE) build . -t "$(image_name)"
	$(CE) create --name="$(image_name)" "$(image_name)"
	$(CE) cp "$(image_name)":"/wehe/app.jar" ./wehe-client.jar
	$(CE) rm "$(image_name)"

wehe-client.tar.gz: wehe-client.jar res
	tar cvzf $@ $^

.PHONY: clean archive

archive: wehe-client.tar.gz

clean:
	$(CE) image rm "$(image_name)"
	rm -f wehe-client.jar wehe-client.tar.gz
