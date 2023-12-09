default: local

gradlew: build.gradle.kts
	gradle wrapper

local: gradlew
	./gradlew publishtoMavenlocal

publish: gradlew
	./gradlew publish

clean: gradlew
	./gradlew clean
	$(RM) -r gradlew gradlew.bat gradle .gradle build

.PHONY: default local publish clean

