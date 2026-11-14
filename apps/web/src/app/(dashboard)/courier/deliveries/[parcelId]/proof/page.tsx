"use client";

import Image from "next/image";
import { ArrowLeft, Camera, Check, Upload } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { FormError } from "@/components/form-field";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, api } from "@/lib/api";
import { proofImageSchema, proofUploadResponseSchema } from "@/lib/courier-deliveries";

export default function CourierPhotoProofPage() {
  const { parcelId } = useParams<{ parcelId: string }>();
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [fileError, setFileError] = useState<string>();
  const [uploadError, setUploadError] = useState<string>();
  const [proofId, setProofId] = useState<string>();
  const [isUploading, setIsUploading] = useState(false);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  function chooseFile(nextFile: File | undefined) {
    setFileError(undefined);
    setUploadError(undefined);
    setProofId(undefined);
    if (!nextFile) return;

    const result = proofImageSchema.safeParse(nextFile);
    if (!result.success) {
      setFile(null);
      setPreviewUrl(null);
      setFileError(result.error.issues[0]?.message ?? "Choose a JPEG or PNG photo no larger than 5 MB.");
      return;
    }

    setFile(result.data);
    setPreviewUrl(URL.createObjectURL(result.data));
  }

  function retakePhoto() {
    setFile(null);
    setPreviewUrl(null);
    setFileError(undefined);
    setUploadError(undefined);
    setProofId(undefined);
  }

  async function uploadPhoto() {
    if (!file) return;

    setIsUploading(true);
    setUploadError(undefined);
    try {
      const body = new FormData();
      body.append("file", file);
      const response = await api<unknown>(`/courier/deliveries/${encodeURIComponent(parcelId)}/proof`, {
        method: "POST",
        body,
      });
      setProofId(proofUploadResponseSchema.parse(response).proofId);
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setUploadError("The photo was rejected. Choose a JPEG or PNG image no larger than 5 MB, then try again.");
      } else if (error instanceof ApiError && error.status === 403) {
        setUploadError("You can’t upload proof because this parcel isn’t assigned to you.");
      } else if (error instanceof ApiError) {
        setUploadError(error.message);
      } else {
        setUploadError("Could not upload the photo. Check your connection and try again.");
      }
    } finally {
      setIsUploading(false);
    }
  }

  return (
    <div className="max-w-2xl">
      <Button
        variant="outline"
        onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}`)}
        className="mb-6 min-h-10"
      >
        <ArrowLeft aria-hidden />
        Parcel details
      </Button>

      <PageHeading title="Photo proof" intro="Take or choose a clear JPEG or PNG photo for this delivery." />

      <Card className="mt-8 max-w-xl">
        <CardContent className="grid gap-5">
          <div>
            <p className="text-sm text-muted-foreground">Parcel</p>
            <p className="mt-1 break-all font-medium tabular">{parcelId}</p>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="proof-photo">Take a photo</Label>
            <Input
              key={file ? "selected" : "empty"}
              id="proof-photo"
              type="file"
              accept="image/*"
              capture="environment"
              disabled={isUploading}
              aria-invalid={!!fileError}
              aria-describedby={fileError ? "proof-photo-error" : "proof-photo-hint"}
              className="h-auto min-h-11 cursor-pointer px-3 py-2 file:mr-3 file:rounded-md file:bg-muted file:px-3 file:py-1.5"
              onChange={(event) => {
                const nextFile = event.currentTarget.files?.[0];
                event.currentTarget.value = "";
                chooseFile(nextFile);
              }}
            />
            {fileError ? (
              <p id="proof-photo-error" role="alert" className="text-sm text-destructive">
                {fileError}
              </p>
            ) : (
              <p id="proof-photo-hint" className="text-sm text-muted-foreground">
                JPEG or PNG, up to 5 MB.
              </p>
            )}
          </div>

          {previewUrl && file && (
            <div className="grid gap-3">
              <div className="overflow-hidden rounded-lg border bg-muted">
                <Image
                  src={previewUrl}
                  alt="Selected photo proof preview"
                  width={1280}
                  height={960}
                  unoptimized
                  className="max-h-80 w-full object-contain"
                />
              </div>
              <p className="break-all text-sm text-muted-foreground">
                {file.name} · {(file.size / (1024 * 1024)).toFixed(2)} MB
              </p>
            </div>
          )}

          {uploadError && <FormError message={uploadError} />}

          {proofId && (
            <div role="status" className="rounded-md border border-emerald-600/30 bg-emerald-600/10 px-3 py-2 text-sm">
              <p className="flex items-center gap-2 font-medium text-emerald-200">
                <Check aria-hidden className="size-4" />
                Photo proof uploaded.
              </p>
              <Button
                onClick={() =>
                  router.push(
                    `/courier/deliveries/${encodeURIComponent(parcelId)}/otp?proofId=${encodeURIComponent(proofId)}`,
                  )
                }
                className="mt-3 min-h-11 w-full"
              >
                Continue to OTP
              </Button>
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <Button
              type="button"
              onClick={() => void uploadPhoto()}
              disabled={!file || isUploading || !!proofId}
              className="min-h-11 w-full"
            >
              <Upload aria-hidden />
              {isUploading ? "Uploading…" : "Upload photo"}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={retakePhoto}
              disabled={(!file && !fileError && !uploadError) || isUploading}
              className="min-h-11 w-full"
            >
              <Camera aria-hidden />
              Retake photo
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
